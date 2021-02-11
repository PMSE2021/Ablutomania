package com.example.ablutomania.bgprocess;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;

import com.example.ablutomania.CustomNotification;
import com.example.ablutomania.bgprocess.ffmpeg.FFMpegProcess;
import com.example.ablutomania.bgprocess.types.Datapoint;
import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;

public class StorageModule {

    private static final double RATE = 50.;
    private static final String CHANID = "StorageModuleNotification";
    private static final String TAG = "StorageModule";
    private static final String VERSION = "1.0";
    private FFMpegProcess mFFmpeg;
    private final Context ctx;

    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_STRT = "ACTION_STRT";
    private final LinkedList<CopyListener> mSensorListeners = new LinkedList<>();

    /* for start synchronization */
    private Long mStartTimeNS = -1L;
    private CountDownLatch mSyncLatch = null;
    private State eState = State.IDLE;

    public enum State {
        IDLE,
        PREPARING,
        RECORDING
    }

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

    public StorageModule.State getState() {
        return eState;
    }

    public StorageModule(Context context, Intent intent) {
        ctx = context;

        Log.d("bgrec", "onStart: " + intent.toString() + " ffmpeg " + mFFmpeg);

        /*
         * start the recording process if there is no ffmpeg instance yet, and no stop intent
         * was sent. When starting a recording, the mSyncLatch variable is initialized!
         */
        boolean doStopRecording = intent != null && ACTION_STOP.equals(intent.getAction()),
                doStartRecording = mFFmpeg == null && !isConnected(ctx);

        /*
         * start the service in foreground mode, so Android won't kill it when running in
         * background. Do this before actually starting the service to not delay the UI while
         * the service is being started.
         */

        //startForeground(CustomNotification.NOTIFICATION_ID, notify(!doStopRecording && doStartRecording));

        if (doStopRecording) {
            stopRecording();
        } else if (doStartRecording) {
            try {
                startRecording();

                /*
                 * monitor the starting status and update the notification once the recording
                 * is started.
                 */
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mSyncLatch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    private Notification notify(String notification) {

        return CustomNotification.updateNotification(ctx, CHANID, notification);
    }

    public static boolean isConnected(Context context) {
        return false;

        /* TODO */
        //Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        //int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        //return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    public void onDestroy() {
        stopRecording();
    }

    public void startRecording() throws Exception {
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
            else
                Log.w("bgrecorder", String.format("no sensor: Type %d ", type));
        }

        boolean gotawakeup = false;
        for (Sensor s : sensors)
            gotawakeup |= s.isWakeUpSensor();

        if (!gotawakeup)
            Log.w("bgrecorder", "no wakeup sensor on device!");

        for (Sensor s : sensors)
            Log.d("bgrecorder", String.format("recording %s %s",
                    s.isWakeUpSensor() ? "wakeup" : "", s.getName()));

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


        mFFmpeg = b.build();

        /*
         * for each sensor there is thread that copies data to the ffmpeg process. For startup
         * synchronization the threads are blocked until the starttime has been set at which
         * point the threadlock will be released.
         */
        int us = (int) (1e6 / RATE);

        mStartTimeNS = -1L;
        mSyncLatch = new CountDownLatch(sensors.size());

        for (int i = 0; i < sensors.size(); i++)
            sm.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    mStartTimeNS = Long.max(event.timestamp, mStartTimeNS);
                    mSyncLatch.countDown();
                    sm.unregisterListener(this);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            }, sensors.get(i), us);

        for (int i = 0; i < sensors.size(); i++) {
            Sensor s = sensors.get(i);
            HandlerThread t = new HandlerThread(s.getName());
            t.start();
            Handler h = new Handler(t.getLooper());
            StorageModule.CopyListener l = new StorageModule.CopyListener(i, RATE, s.getName());
            int delay = s.isWakeUpSensor() ? s.getFifoMaxEventCount() / 2 * us : 1;
            mSensorListeners.add(l);
        }
    }

    public void stopRecording() {
        if (mFFmpeg != null) {
            try {
                /* if stuck in preparing state */
                for (int i = 0; i < mSyncLatch.getCount(); i++)
                    mSyncLatch.countDown();

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



    private class CopyListener {
        private final int index;
        private final long mDelayUS;
        private long mSampleCount;
        private long mOffsetUS;
        private final String mName;

        private OutputStream mOutRot;
        private OutputStream mOutGyro;
        private OutputStream mOutAccel;
        private OutputStream mOutMag;
        private OutputStream mOutML;
        private ByteBuffer mBuf;
        private long mLastTimestamp = -1;
        private boolean mFlushCompleted = false;

        /**
         * @param i    index
         * @param rate frequency in Hz
         * @param name name
         */
        public CopyListener(int i, double rate, String name) {
            index = i;
            mOutRot = null;
            mOutGyro = null;
            mOutAccel = null;
            mOutMag = null;
            mOutML = null;
            mName = name;
            mDelayUS = (long) (1e6 / rate);
            mSampleCount = 0;
            mOffsetUS = 0;
        }

        public void run() {

            if (DataPipeline.getOutputFIFO().size() < 1)
                return;

            Datapoint dp = DataPipeline.getOutputFIFO().get();

            // set buffer size for each sensor + ML result
            ByteBuffer mBufRot = ByteBuffer.allocate(4 * 5);
            ByteBuffer mBufGyro = ByteBuffer.allocate(4 * 3);
            ByteBuffer mBufAccel = ByteBuffer.allocate(4 * 3);
            ByteBuffer mBufMag = ByteBuffer.allocate(4 * 3);
            ByteBuffer mBufML = ByteBuffer.allocate(4 * 1);

            // check if data pipeline is empty or filed
            if (dp == null)
                return;

            // get data from data pipeline
            float[] rotData = dp.getRotationVectorData();
            float[] gyroData = dp.getGyroscopeData();
            float[] accelData = dp.getAccelerometerData();
            float[] magData = dp.getMagnetometerData();
            float[] mlData = dp.getMlResult();

            // put data in buffer
            for (float v : rotData)
                mBufRot.putFloat(v);
            for (float v : gyroData)
                mBufGyro.putFloat(v);
            for (float v : accelData)
                mBufAccel.putFloat(v);
            for (float v : magData)
                mBufMag.putFloat(v);
            for (float v : mlData)
                mBufML.putFloat(v);

            try {
                //write stream in matroska file

                if (mOutRot == null)
                    mOutRot = mFFmpeg.getOutputStream(0);
                mOutRot.write(mBufRot.array());
                if (mOutGyro == null)
                    mOutGyro = mFFmpeg.getOutputStream(1);
                mOutGyro.write(mBufGyro.array());
                if (mOutAccel == null)
                    mOutAccel = mFFmpeg.getOutputStream(2);
                mOutAccel.write(mBufAccel.array());
                if (mOutMag == null)
                    mOutMag = mFFmpeg.getOutputStream(3);
                mOutMag.write(mBufMag.array());
                if (mOutML == null)
                    mOutML = mFFmpeg.getOutputStream(4);
                mOutML.write(mBufML.array());
            } catch(Exception e) {
                Log.e(TAG, "file not found");
            }

        }
    }
}


/* DataPipeline.Datapoint dp;
ByteBuffer mBufRot = ByteBuffer.allocate(4 * 5);
ByteBuffer mBufAccel = ByteBuffer.allocate(4 * 3);
dp = DataPipeline.getFromFFmpegBuffer();
if(null == dp) return;
float[] rotData = dp.getRotationVectorData();
float[] gyroData = dp.getGyroscopeData();
for (float v : rotData) mBufRot.putFloat(v);
for() mOut = mFFmpeg.getOutputStream(indexRotationVector);
mOut.write(mBufRot.array());
mOut = mFFmpeg.getOutputStream(indexAccel);
mOut.write(mBufRot.array()); */