package com.example.ablutomania.bgprocess;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.example.ablutomania.CustomNotification;
import com.example.ablutomania.R;

import com.example.ablutomania.SystemStatus;

import com.example.ablutomania.bgprocess.types.Datapoint;
import com.example.ablutomania.bgprocess.types.FIFO;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;

/**
 * Created by shoesch on 09.02.2021
 */

public class SensorModule implements Runnable {
    private static final double RATE = 50.;
    private static final String CHANID = "SensorModuleNotification";
    private static final String TAG = "SensorModule";
    private Context ctx;
    private Handler handler;

    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_STRT = "ACTION_STRT";
    private final LinkedList<CopyListener> mSensorListeners = new LinkedList<>();

    LinkedList<Sensor> sensors = new LinkedList<>();

    private LinkedList<FIFO<float[]>> mFifos = new LinkedList<>();

    private PowerManager.WakeLock mwl = null;

    /* special WakeLock tag for Huawei Devices, see
     * https://stackoverflow.com/questions/39954822/battery-optimizations-wakelocks-on-huawei-emui-4-0
     */
    private static final String WAKE_LOCK_TAG = "LocationManagerService";

    /* for start synchronization */
    private Long mStartTimeNS = -1L;
    private CountDownLatch mSyncLatch = null;
    private State eState = State.IDLE;
    private long mLastTimestamp = -1;

    private static final int[] types = {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD
    };

    public enum State {
        IDLE,
        PREPARING,
        RECORDING
    }

    @SuppressLint("InvalidWakeLockTag")
    public SensorModule(Context context, Intent intent) {
        ctx = context;
        handler = new Handler(Looper.getMainLooper());

        if (mwl == null) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            mwl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
        }

        /*
         * start the recording process if there is no ffmpeg instance yet, and no stop intent
         * was sent. When starting a recording, the mSyncLatch variable is initialized!
         */
        boolean doStopRecording = intent != null && ACTION_STOP.equals(intent.getAction()),
                doStartRecording = !isConnected(ctx);

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
                            SensorModule.this.notify(false);
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

    @Override
    public void run() {
        handler.postDelayed(this, (long) (1e3 / RATE));

        long mOffset = 0;
        if (mLastTimestamp == -1) {
            mLastTimestamp = System.currentTimeMillis();
        } else {
            long t = System.currentTimeMillis();
            mOffset = t - mLastTimestamp;
            mLastTimestamp = t;
        }

        //Log.d(TAG, String.format("running: offset = %dms", mOffset));

        /*
         * monitor the output fifos of each sensor and create datapoints if each values is
         * available.
         */
        shiftDatapointsToPipeline();
    }

    public static String getDefaultFileName(Context context) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        @SuppressLint("HardwareIds") String aid = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return df.format(new Date()) + "_Ablutomania_" + aid + ".mkv";
    }

    private Notification notify(boolean isStopped) {
        /*
         * directly update the notification text, when background recording started/stopped
         * by the system.
         */
        final String[] notifyContent = {
                ctx.getString(R.string.notification_recording_paused),  // IDLE
                ctx.getString(R.string.notification_recording_preping), // PREPARING
                ctx.getString(R.string.notification_recording_ongoing)  // RECORDING
        };

        if(!isStopped) {
            if (mSyncLatch != null && mSyncLatch.getCount() == 0) {
                eState = State.RECORDING;
            } else {
                eState = State.PREPARING;
            }
        }
        else {
            eState = State.IDLE;
        }

        return CustomNotification.updateNotification(ctx, CHANID, notifyContent[eState.ordinal()]);
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
        synchronized (this) {
            DataPipeline.getInputFIFO().clear();
        }
    }

    public void startRecording() throws Exception {
        mwl.acquire();

        /*
         *  Try to record this list of sensors. We go through this list and get them as wakeup
         *  sensors first. Terminate if there is no wakeup supported (otherwise a wake-lock would
         *  be required). Then get all sensors as non-wakeups and select only those that are there.
         */
        final SensorManager sm = (SensorManager) ctx.getSystemService(ctx.SENSOR_SERVICE);

        for (int type : types) {
            Sensor s = sm.getDefaultSensor(type, true);

            if (s == null)
                s = sm.getDefaultSensor(type);

            if (s != null)
                sensors.add(s);
            else
                Log.w(TAG, String.format("no sensor: Type %d ", type));
        }

        boolean gotawakeup = false;
        for (Sensor s : sensors)
            gotawakeup |= s.isWakeUpSensor();

        if (!gotawakeup)
            Log.w(TAG, "no wakeup sensor on device!");

        for (Sensor s : sensors)
            Log.d(TAG, String.format("recording %s %s",
                    s.isWakeUpSensor() ? "wakeup" : "", s.getName()));

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
            int delay = /* s.isWakeUpSensor() ? s.getFifoMaxEventCount() / 2 * us :*/ 1;
            sm.registerListener(l, s, us, delay, h);
            mSensorListeners.add(l);
            mFifos.add(new FIFO<>());
        }
    }

    public void stopRecording() {
        /* if stuck in preparing state */
        for (int i=0; i < mSyncLatch.getCount(); i++)
            mSyncLatch.countDown();

        SensorManager sm = (SensorManager) ctx.getSystemService(ctx.SENSOR_SERVICE);

        for (CopyListener l : mSensorListeners)
            sm.flush(l);

        mwl.release();
        notify(true);

        SystemStatus.GetInstance().setStatusWarning(ctx);

    }


    private void shiftDatapointsToPipeline() {
        synchronized (this) {

            boolean bDpAvailable;

            do {
                String sLog;
                bDpAvailable = true;

                for (FIFO<float[]> list : mFifos) {
                    if (!(list.size() > 0)) {
                        bDpAvailable = false;
                    }
                }

                //sLog = "FIFO lists:";
                //for (FIFO f : mFifos)
                //    sLog += String.format(" %d", f.size());
                //Log.i(TAG, sLog);

                if (bDpAvailable) {
                    try {
                        Datapoint dp = new Datapoint();

                        for (int i = 0; i < mFifos.size(); i++) {
                            // Write mFifos to data pipeline
                            switch (sensors.get(i).getType()) {
                                case Sensor.TYPE_ACCELEROMETER: {
                                    dp.setAccelerometerData(mFifos.get(i).get());
                                    break;
                                }
                                case Sensor.TYPE_GYROSCOPE: {
                                    dp.setGyroscopeData(mFifos.get(i).get());
                                    break;
                                }
                                case Sensor.TYPE_MAGNETIC_FIELD: {
                                    dp.setMagnetometerData(mFifos.get(i).get());
                                    break;
                                }
                                case Sensor.TYPE_ROTATION_VECTOR: {
                                    dp.setRotationVectorData(mFifos.get(i).get());
                                    break;
                                }
                                default:
                                    Log.e(TAG, String.format("unknown sensor type:  %d" + sensors.get(i).getType()));

                                    SystemStatus.GetInstance().setStatusError(ctx);
                            }
                        }
                        // Add complete datapoint to pipeline
                        DataPipeline.getInputFIFO().put(dp);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sLog = "Exception in bDataAvailable: ";
                        for (FIFO f : mFifos)
                            sLog += String.format(" %d", f.size());
                        Log.e(TAG, sLog);
                    }
                }
            } while (bDpAvailable);
        }
    }

    private class CopyListener implements SensorEventListener, SensorEventListener2 {
        private final int index;
        private final long mDelayUS;
        private long mSampleCount;
        private long mOffsetUS;
        private final String mName;

        private long mLastTimestamp = -1;

        /**
         * @param i     index
         * @param rate  frequency in Hz
         * @param name  name
         */
        public CopyListener(int i, double rate, String name) {
            index = i;
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
                 *  multiple stream synchronization, wait until a global timestamp was set,
                 *  and only start pushing events after this timestamp.
                 */
                if (sensorEvent.timestamp < mStartTimeNS)
                    return;

                long sensorDelay = 0;   /* sensorDelay is in use to debug sensor delay over time.*/
                if (mLastTimestamp != -1)
                    sensorDelay = (sensorEvent.timestamp - mLastTimestamp) / 1000;
                mOffsetUS += sensorDelay;
                mLastTimestamp = sensorEvent.timestamp;

                /*
                 *    Uncomment following to lines to allow debugging of sensor delay over time by logcat.
                 */
                //String logText = String.format("logSampleDelay %.4f|%.4f|%d|%s", sensorDelay / 1e6, mOffsetUS / 1e6, mLastTimestamp, mName);
                //Log.i(TAG,logText);

                /*
                 * check whether or not interpolation is required
                 */
                if (Math.abs(mOffsetUS) - mDelayUS > mDelayUS)
                    Log.e(TAG, String.format(
                            "sample delay too large %.4f %s", mOffsetUS / 1e6, mName));

                if (mOffsetUS < mDelayUS)      // too fast -> remove
                    return;

                while (mOffsetUS >= mDelayUS) { // add new samples, might be too slow
                    synchronized (SensorModule.this) {
                        mFifos.get(index).put(sensorEvent.values);
                    }
                    mOffsetUS -= mDelayUS;
                    mSampleCount++;
                }

            } catch (Exception e) {
                e.printStackTrace();
                SensorManager sm = (SensorManager) ctx.getSystemService(ctx.SENSOR_SERVICE);
                sm.unregisterListener(this);
                Log.e(TAG, String.format("%d samples written %s", mSampleCount, mName));
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }

        @Override
        public void onFlushCompleted(Sensor sensor) {
        }
    }
}
