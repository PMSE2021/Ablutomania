package com.example.ablutomania.bgprocess;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
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
    private static final int SIZE_OF_FLOAT = 4; // 4 bytes
    private static final String CHANID = "StorageModuleNotification";
    private static final String TAG = "StorageModule";
    private static final String VERSION = "1.0";
    private FFMpegProcess mFFmpeg;
    private final Context ctx;
    private Handler handler;
    private LinkedList<CopyListener> mOutputStreamListeners = new LinkedList<>();

    /* I would rather like to use enum with ordinal(), but this isn't
       recommend in java. So let's do it this (ugly) way. */
    private static final int STREAMTYPE_ROTATION_VECTOR = 0;
    private static final int STREAMTYPE_ACCELEROMETER   = 1;
    private static final int STREAMTYPE_GYROSCOPE       = 2;
    private static final int STREAMTYPE_MAGNETIC_FIELD  = 3;
    private static final int STREAMTYPE_ML_RESULT       = 4;

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

        try {
            initFFMPEG();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            notify("FFMPEG initialisation failed");
            Log.e(TAG, SystemStatus.GetInstance().getStatus().toString());
            SystemStatus.GetInstance().setStatusError(ctx);
            Log.e(TAG, SystemStatus.GetInstance().getStatus().toString());
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
        class StreamConfig {
            public int mSensorType;
            public int mStreamType;
            public String mName;

            public StreamConfig(int sensorType, int streamType, String name) {
                mSensorType = sensorType;
                mStreamType = streamType;
                mName = name;
            }
        };

        final SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        StreamConfig[] sConfig = {
                new StreamConfig( Sensor.TYPE_ROTATION_VECTOR,  STREAMTYPE_ROTATION_VECTOR  , "ROTATION_VECTOR_SENSOR"  ),
                new StreamConfig( Sensor.TYPE_ACCELEROMETER,    STREAMTYPE_ACCELEROMETER    , "ACCELEROMETER_SENSOR"    ),
                new StreamConfig( Sensor.TYPE_GYROSCOPE,        STREAMTYPE_GYROSCOPE        , "GYROSCOPE_SENSOR"        ),
                new StreamConfig( Sensor.TYPE_MAGNETIC_FIELD,   STREAMTYPE_MAGNETIC_FIELD   , "MAGNETIC_FIELD_SENSOR"   ),
                new StreamConfig( -1,                           STREAMTYPE_ML_RESULT        , "ML_RESULT"               )
        };
        LinkedList<Stream> streams = new LinkedList<>();

        for(StreamConfig config : sConfig) {
            if(-1 == config.mSensorType) {
                /* Add ML stream */
                streams.add(new Stream(null, config.mStreamType, config.mName));
            } else {
                /* Add Sensor stream */
                Sensor s = sm.getDefaultSensor(config.mSensorType, true);

                if (s == null)
                    s = sm.getDefaultSensor(config.mSensorType);

                if (s != null)
                    streams.add(new Stream(s, config.mStreamType, config.mName));
            }
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


        for (Stream s : streams)
            b.addAudio(format, RATE, getNumChannels(s.getType()))
                    .setStreamTag("name", s.getName());

        mFFmpeg = b.build();


        // setup threads for eache output stream
        int streamNum = 0;
        for (Stream s : streams) {
            HandlerThread t = new HandlerThread(s.getName()); t.start();
            Handler h = new Handler(t.getLooper());
            CopyListener l = new CopyListener(streamNum, h, s.getName(), s.getType());

            mOutputStreamListeners.add(l);
            streamNum++;
        }

        Log.i(TAG, String.format("now we have %d listener lets start each of them in his own thread", mOutputStreamListeners.size()));
    }

    public void onDestroy() {
        if (mFFmpeg != null) {
            try {
                mFFmpeg.terminate();
                mFFmpeg.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mFFmpeg = null;
        DataPipeline.getOutputFIFO().clear();
    }

    private int getNumChannels(int type) throws Exception {
        switch (type) {
            case STREAMTYPE_ACCELEROMETER:
            case STREAMTYPE_GYROSCOPE:
            case STREAMTYPE_MAGNETIC_FIELD:
                return 3;

            case STREAMTYPE_ROTATION_VECTOR:
                return 5;

            case STREAMTYPE_ML_RESULT:
                return 1;

            default:
                throw new Exception(String.format("unknown number of channels for type %d", type));
        }
    }

    @Override
    public void run() {
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

        Datapoint dp;
        FIFO<Datapoint> mOutputFIFO = DataPipeline.getOutputFIFO();

        //Log.i(TAG, String.format("OutputFIFO size: %d", mOutputFIFO.size()));

        try {
            int maxDatapoint = 10; //mOutputFIFO.size();
            do {
                //get values from output FIFO
                dp = mOutputFIFO.get();

                // check if data pipeline is empty or filed
                if (dp == null) {
                    //Log.i(TAG, String.format("no data in OutputFIFO -> return"));
                    return;
                }

                for (CopyListener l : mOutputStreamListeners) {
                    float[] data;
                    int curType = l.getType();

                    /* Set buffer size */
                    ByteBuffer mBuf = ByteBuffer.allocate(SIZE_OF_FLOAT * getNumChannels(l.getType())); // set buffer size
                    mBuf.order(ByteOrder.nativeOrder());

                    /* get data from data pipeline */
                    switch(curType) {
                        case STREAMTYPE_ROTATION_VECTOR:    { data = dp.getRotationVectorData();    break; }
                        case STREAMTYPE_GYROSCOPE:          { data = dp.getGyroscopeData();         break; }
                        case STREAMTYPE_ACCELEROMETER:      { data = dp.getAccelerometerData();     break; }
                        case STREAMTYPE_MAGNETIC_FIELD:     { data = dp.getMagnetometerData();      break; }
                        case STREAMTYPE_ML_RESULT:          { data = dp.getMlResult();              break; }
                        default:
                            throw new Exception("Unhandled copylistener reference");
                    }

                    for(float v : data) // put data in buffer
                        mBuf.putFloat(v);
                    l.onToStream(mBuf);
                    mBuf.clear(); // clear buffer
                }

                //Log.i(TAG, String.format("OutputFIFO size: %d", mOutputFIFO.size()));

                maxDatapoint--;
            } while ((maxDatapoint > 0) && (mOutputFIFO.size() > 0));
        } catch(Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
    }

    /* get free storage in MB */
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

    private class CopyListener {
        private Handler mHandler;
        private OutputStream mOut;

        private final int mIndex;
        private final String mName;
        private int mType;


        /**
         * @param i
         * @param h
         * @param name
         */
        public CopyListener(int i, Handler h, String name, int type) {
            mHandler = h;
            mIndex = i;
            mOut = null;
            mName = name;
            mType = type;
        }

        public void onToStream(ByteBuffer buf) {
            //Log.i(TAG, String.format("onToStream is called from: %s at type: %d", mName, mType));

            //Log.e(TAG, SystemStatus.GetInstance().getStatus().toString());
            //SystemStatus.GetInstance().setStatusError(ctx);
            //Log.e(TAG, SystemStatus.GetInstance().getStatus().toString());

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mOut == null)
                            mOut = mFFmpeg.getOutputStream(mIndex);

                        if (mOut != null) {
                            mOut.write(buf.array());
                            //Log.i(TAG, String.format("%s: data %d successfully written to stream (%d)", mName, cnt, (int) Thread.currentThread().getId()));
                        } else {
                            //Log.e(TAG, String.format("%s: data %d failed writing to stream", mName, cnt));
                        }
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                }
            });

        }

        public int getType() { return mType; }
        public String getName() { return mName; }
    } /* end CopyListener */

    private class Stream {
        private Sensor mSensor;
        private int mType;
        private String mName;

        public Stream(Sensor sensor, int type, String name) {
            mSensor = sensor;
            mType = type;
            mName = name;
        }

        public Sensor getSensor() { return mSensor; }
        public int getType() { return mType; }
        public String getName() { return mName; }
    } /* end Stream */
} /* end StorageModule */