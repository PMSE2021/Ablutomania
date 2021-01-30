package com.example.ablutomania.bgrecorder;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import com.example.ablutomania.CustomNotification;
import com.example.ablutomania.R;
import com.example.ablutomania.ffmpeg.FFMpegProcess;

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

/** On start, and if not already running, this Service spawns an ffmpeg instance to
 * record all inertial motion sensor in the background.
 *
 * Created by phil on 07.08.18.
 */

public class RecorderService extends Service {
    private static final double RATE = 50.;
    private static final String CHANID = "RecorderServiceNotification";
    private static final String TAG = "RecorderService";
    private String VERSION = "1.21";
    private FFMpegProcess mFFmpeg;

    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_STRT = "ACTION_STRT";
    private LinkedList<CopyListener> mSensorListeners = new LinkedList<>();

    /* for start synchronization */
    private Long mStartTimeNS = -1L;
    private CountDownLatch mSyncLatch = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
        String aid = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return df.format(new Date()) + "_Ablutomania_" + aid + ".mkv";
    }

    @Override
    public void onCreate()  {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        Log.d("bgrec", "onStart: " + intent.toString() + " ffmpeg " + mFFmpeg);

        /*
         * start the recording process if there is no ffmpeg instance yet, and no stop intent
         * was sent. When starting a recording, the mSyncLatch variable is initialized!
         */
        boolean doStopRecording = intent != null && ACTION_STOP.equals(intent.getAction()),
                doStartRecording = mFFmpeg == null && !isConnected(this);

        /*
         * start the service in foreground mode, so Android won't kill it when running in
         * background. Do this before actually starting the service to not delay the UI while
         * the service is being started.
         */
        startForeground(CustomNotification.NOTIFICATION_ID, notify(!doStopRecording && doStartRecording));

        if (doStopRecording) {
            stopRecording();
        }
        else if (doStartRecording) {
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
                            RecorderService.this.notify(false);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }

        return START_STICKY;
    }


    private Notification notify(boolean ispreparing) {
        /*
         * directly update the notification text, when background recording started/stopped
         * by the system.
         */
        String contentText = getString(
                        ispreparing ?
                                R.string.notification_recording_preping :
                        mFFmpeg != null ?
                        mSyncLatch != null && mSyncLatch.getCount() == 0 ?
                                R.string.notification_recording_ongoing :
                                R.string.notification_recording_preping :
                                R.string.notification_recording_paused);

        return CustomNotification.updateNotification(RecorderService.this, CHANID, contentText);
    }


    public static boolean isConnected(Context context) {
        return false;

        /* TODO */
        //Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        //int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        //return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    @Override
    public void onDestroy() {
        stopRecording();
    }

    public void startRecording() throws Exception {
        String platform = Build.BOARD + " " + Build.DEVICE + " " + Build.VERSION.SDK_INT,
                output = getDefaultOutputPath(getApplicationContext()),
                android_id = Settings.Secure.getString(
                        getContentResolver(), Settings.Secure.ANDROID_ID),
                format = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? "f32le" : "f32be";

        /*
         *  Try to record this list of sensors. We go through this list and get them as wakeup
         *  sensors first. Terminate if there is no wakeup supported (otherwise a wake-lock would
         *  be required). Then get all sensors as non-wakeups and select only those that are there.
         */
        final SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
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
        FFMpegProcess.Builder b = new FFMpegProcess.Builder(getApplicationContext())
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
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            }, sensors.get(i), us);

        for (int i = 0; i < sensors.size(); i++) {
            Sensor s = sensors.get(i);
            HandlerThread t = new HandlerThread(s.getName()); t.start();
            Handler h = new Handler(t.getLooper());
            CopyListener l = new CopyListener(i, RATE, s.getName());
            int delay = s.isWakeUpSensor() ? s.getFifoMaxEventCount() / 2 * us : 1;
            sm.registerListener(l, s, us, delay, h);
            mSensorListeners.add(l);
        }
    }


    public void stopRecording() {
        if (mFFmpeg != null) {
            try {
                /* if stuck in preparing state */
                for (int i=0; i < mSyncLatch.getCount(); i++)
                    mSyncLatch.countDown();

                SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);

                for (CopyListener l : mSensorListeners)
                    sm.flush(l);

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

    private class CopyListener implements SensorEventListener, SensorEventListener2 {
        private final int index;
        private final long mDelayUS;
        private long mSampleCount;
        private long mOffsetUS;
        private final String mName;

        private OutputStream mOut;
        private ByteBuffer mBuf;
        private long mLastTimestamp = -1;
        private boolean mFlushCompleted = false;

        /**
         * @param i     index
         * @param rate  frequency in Hz
         * @param name  name
         */
        public CopyListener(int i, double rate, String name) {
            index = i;
            mOut = null;
            mName = name;
            mDelayUS = (long) (1e6 / rate);
            mSampleCount = 0;
            mOffsetUS = 0;
        }

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            try {
                /*
                 * wait until the mStartTimeNS is cleared. This will be done by the SyncLockListener
                 */
                mSyncLatch.await();

                /*
                 * if a flush was completed, the sensor process is done, and the recording
                 * will be stopped. Hence the output channel is closed to let ffmpeg know,
                 * that the recording is finished. This will then lead to an IOException,
                 * which cleanly exits the whole process.
                 */
                if (mFlushCompleted)
                    mOut.close();

                /*
                 *  multiple stream synchronization, wait until a global timestamp was set,
                 *  and only start pushing events after this timestamp.
                 */
                if (sensorEvent.timestamp < mStartTimeNS)
                    return;

                if (mLastTimestamp != -1)
                    mOffsetUS += (sensorEvent.timestamp - mLastTimestamp) / 1000;
                mLastTimestamp = sensorEvent.timestamp;


                /*
                 * create an output buffer, once created only delete the last sample. Insert
                 * values afterwards.
                 */
                if (mBuf == null) {
                    mBuf = ByteBuffer.allocate(4 * sensorEvent.values.length);
                    mBuf.order(ByteOrder.nativeOrder());
                    Log.e("bgrec", String.format("%s started at %d", mName, sensorEvent.timestamp));
                } else
                    mBuf.clear();

                /*
                 * see https://stackoverflow.com/questions/30279065/how-to-get-the-euler-angles-from-the-rotation-vector-sensor-type-rotation-vecto
                 * https://developer.android.com/reference/android/hardware/SensorEvent#sensor
                 */

                for (float v : sensorEvent.values)
                    mBuf.putFloat(v);

                /*
                 * check whether or not interpolation is required
                 */
                if (Math.abs(mOffsetUS) - mDelayUS > mDelayUS)
                    Log.e("bgrec", String.format(
                            "sample delay too large %.4f %s", mOffsetUS / 1e6, mName));

                if (mOut == null)
                    mOut = mFFmpeg.getOutputStream(index);

                if (mOffsetUS < mDelayUS)      // too fast -> remove
                    return;

                while (mOffsetUS > mDelayUS) { // add new samples, might be too slow
                    mOut.write(mBuf.array());
                    mOffsetUS -= mDelayUS;
                    mSampleCount++;
                }
            } catch (Exception e) {
                e.printStackTrace();
                SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
                sm.unregisterListener(this);
                Log.e("bgrec", String.format("%d samples written %s", mSampleCount, mName));
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }

        @Override
        public void onFlushCompleted(Sensor sensor) {
            mFlushCompleted = true;
        }
    }
}
