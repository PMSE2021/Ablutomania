package com.example.ablutomania.bgprocess;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;

import com.example.ablutomania.CustomNotification;
import com.example.ablutomania.SystemStatus;
import com.example.ablutomania.bgprocess.ffmpeg.FFMpegProcess;
import com.example.ablutomania.bgprocess.types.Datapoint;
import com.example.ablutomania.bgprocess.types.FIFO;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;

public class StorageModule implements Runnable{

    private static final double RATE = 50.;
    private static final String CHANID = "StorageModuleNotification";
    private static final String TAG = "StorageModule";
    private static final String VERSION = "1.0";
    private long mLastTimestamp = -1;
    private FFMpegProcess mFFmpeg;
    private final Context ctx;
    private Handler handler;
    private OutputStream mOutRot;
    private OutputStream mOutGyro;
    private OutputStream mOutAccel;
    private OutputStream mOutMag;
    private OutputStream mOutML;

    public static String getCurrentDateAsIso() {
        // see https://stackoverflow.com/questions/3914404/how-to-get-current-moment-in-iso-8601-format
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
    }

    public static String getDefaultOutputPath(Context context) {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        return new File(path, getDefaultFileName(context)).toString();
    }

    public static String getDefaultFileName(Context context) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        @SuppressLint("HardwareIds") String aid = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return df.format(new Date()) + "_Ablutomania_" + aid + ".mkv";
    }

    public StorageModule(Context context) {
        ctx = context;
        handler = new Handler(Looper.getMainLooper());

        mOutRot = null;
        mOutGyro = null;
        mOutAccel = null;
        mOutMag = null;
        mOutML = null;

        try{
            initFFMPEG();
        }catch(Exception e){
            Log.e(TAG, e.getMessage());
            notify("FFMPEG initialisation failed");
            SystemStatus.GetInstance().setStatusError();
        }
    }

    private Notification notify(String notification) {

        return CustomNotification.updateNotification(ctx, CHANID, notification);
    }

    public void initFFMPEG() throws Exception {
        @SuppressLint("HardwareIds") String platform = Build.BOARD + " " + Build.DEVICE + " " + Build.VERSION.SDK_INT,
                output = getDefaultOutputPath(ctx.getApplicationContext()),
                android_id = Settings.Secure.getString(
                        ctx.getContentResolver(), Settings.Secure.ANDROID_ID),
                format = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "f32le" : "f32be";

        /*
         *  Try to record this list of sensors. We go through this list and get them as wakeup
         *  sensors first. Terminate if there is no wakeup supported (otherwise a wake-lock would
         *  be required). Then get all sensors as non-wakeups and select only those that are there.
         */
        final SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        int[] types = {
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_MAGNETIC_FIELD
        };
        LinkedList<Sensor> sensors = new LinkedList<>();

        for (int type : types) {
            Sensor s = sm.getDefaultSensor(type, true);

            if (s == null)
                s = sm.getDefaultSensor(type);

            if (s != null)
                sensors.add(s);
        }
        /*
         * build and start the ffmpeg process, which transcodes into a matroska file.
         */
        FFMpegProcess.Builder b = new FFMpegProcess.Builder(ctx.getApplicationContext())
                .setOutput(output, "matroska")
                .setCodec("a", "wavpack")
                .addOutputArgument("-shortest")
                .setTag("recorder", "Ablutomania " + VERSION)
                .setTag("android_id", android_id)
                .setTag("platform", platform)
                .setTag("fingerprint", Build.FINGERPRINT)
                .setTag("beginning", getCurrentDateAsIso());

        for (Sensor s : sensors)
            b
                    .addAudio(format, RATE, getNumChannels(s))
                    .setStreamTag("name", s.getName());

        b.addAudio(format, RATE, 1 )
                .setStreamTag("name", "mlResult");


        mFFmpeg = b.build();
    }

    public void onDestroy() {
        if (mFFmpeg != null) {
            try {

                mFFmpeg.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mFFmpeg = null;

    }

    private int getNumChannels(Sensor s) throws Exception {
        /*
         * https://developer.android.com/reference/android/hardware/SensorEvent#sensor
         */
        switch (s.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_MAGNETIC_FIELD:
                return 3;

            case Sensor.TYPE_ROTATION_VECTOR:
                return 5;

            case Sensor.TYPE_RELATIVE_HUMIDITY:
            case Sensor.TYPE_PRESSURE:
            case Sensor.TYPE_LIGHT:
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return 1;

            default:
                throw new Exception("unknown number of channels for " + s.getName());
        }
    }


    @Override
    public void run() {
        // TODO: Function is now called cyclic, but not in the specified period

        //check, if watch is charging
        /*if (isCharging(ctx) = true) {
            Log.i(TAG, "Charging device");
            return;
        }*/

        //check, if free memory < 5 MB: error (1 min = 0.18 MB; 1 h = 11 MB)
        if (freeStorage() < 5) {
            Log.e(TAG, "no free storage space available");
            notify("You ran out of free storage");
        }


        handler.postDelayed(this, (long) (1e3 / RATE));

        long mOffset = 0;
        if (mLastTimestamp == -1) {
            mLastTimestamp = System.currentTimeMillis();
        } else {
            long t = System.currentTimeMillis();
            mOffset = t - mLastTimestamp;
            mLastTimestamp = t;
        }

        Log.d(TAG, String.format("running: offset = %dms", mOffset));

        Datapoint dp;
        FIFO<Datapoint> mOutputFIFO = DataPipeline.getOutputFIFO();

        // set buffer size for each sensor + ML result
        ByteBuffer mBufRot = ByteBuffer.allocate(4 * 5);
        ByteBuffer mBufAccel = ByteBuffer.allocate(4 * 3);
        ByteBuffer mBufGyro = ByteBuffer.allocate(4 * 3);
        ByteBuffer mBufMag = ByteBuffer.allocate(4 * 3);
        ByteBuffer mBufML = ByteBuffer.allocate(4 * 1);

        int maxDatapoint = 10;
        do {
            Log.i(TAG, String.format("OutputFIFO size: %d", mOutputFIFO.size()));

            //get values from output FIFO
            dp = mOutputFIFO.get();

            // check if data pipeline is empty or filed
            if (dp == null) {
                return;
            }

            // get data from data pipeline
            float[] rotData = dp.getRotationVectorData();
            float[] accelData = dp.getAccelerometerData();
            float[] gyroData = dp.getGyroscopeData();
            float[] magData = dp.getMagnetometerData();
            float[] mlData = dp.getMlResult();

            // put data in buffer
            if(rotData != null){
                for (float v : rotData)
                    mBufRot.putFloat(v);
            }
            if(accelData != null){
                for (float v : accelData)
                    mBufAccel.putFloat(v);
            }
            if (gyroData != null){
                for (float v : gyroData)
                    mBufGyro.putFloat(v);
            }
            if (magData != null){
                for (float v : magData)
                    mBufMag.putFloat(v);
            }
            if (mlData != null){
                for (float v : mlData)
                    mBufML.putFloat(v);
            }


            try {
                //write stream in matroska file
                // TODO: Something is wrong in following code
                /*if (mOutRot == null)
                    mOutRot = mFFmpeg.getOutputStream(0);
                mOutRot.write(mBufRot.array()); */
                /*if (mOutAccel == null)
                    mOutAccel = mFFmpeg.getOutputStream(0);
                mOutAccel.write(mBufAccel.array());*/
                if (mOutGyro == null)
                    Log.e(TAG, "try to get output stream");
                    mOutGyro = mFFmpeg.getOutputStream(1);
                    Log.e(TAG, "got output stream");
                mOutGyro.write(mBufGyro.array());
                Log.e(TAG, "write datastream");
                /*if (mOutMag == null)
                    mOutMag = mFFmpeg.getOutputStream(3);
                mOutMag.write(mBufMag.array());
                if (mOutML == null)
                    mOutML = mFFmpeg.getOutputStream(4);
                mOutML.write(mBufML.array());

                 */


                // Clear buffers
                mBufRot.clear();
                mBufGyro.clear();
                mBufAccel.clear();
                mBufMag.clear();
                mBufML.clear();

                maxDatapoint--;
            } catch(Exception e) {
                Log.e(TAG, "file not found");
                notify("output stream not found");
            }
        } while ((maxDatapoint > 0) && (mOutputFIFO.size() > 0));
    }

    //get free storage in MB
    public float freeStorage(){
        StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
        float free = (float) ((statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong()) / 1e6);
        return free;
    }

    /*public static boolean isCharging(Context context) {

        //Determine the current charging state
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        // Are we charging?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        return status == BatteryManager.BATTERY_STATUS_CHARGING;
    }*/
}

