package com.example.ablutomania.bgprocess;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.example.ablutomania.CustomNotification;
import com.example.ablutomania.bgprocess.mlmodel.MLWrapper;

/*
 * Created by shoesch on 10.02.2021
 */

public class BackgroundService extends Service {
    private static final String CHANID = "BackgroundServiceNotification";
    private static final String TAG = "BgService";

    private SensorModule mSensorModule;
    private MLWrapper mMLWrapper;
    private StorageModule mStorageModule;

    private int NOTIFICATION_ID = 0x007;

    private static final int STATE_IDLE = 0;
    private static final int STATE_RUNNING = 1;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate()  {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        /**
         * start the service in foreground mode, so Android won't kill it when running in
         * background. Do this before actually starting the service to not delay the UI while
         * the service is being started.
         */
        startForeground(NOTIFICATION_ID, CustomNotification.updateNotification(this, CHANID, "App has started in foreground"));

        // Create instance and start storage module
        mStorageModule = new StorageModule((Context)BackgroundService.this);
        new Thread(mStorageModule).start();

        // Create instance and start sensor module
        mSensorModule = new SensorModule((Context)BackgroundService.this , intent);
        new Thread(mSensorModule).start();

        // Create instance and start thread MLWrapper
        mMLWrapper = new MLWrapper((Context)BackgroundService.this);
        new Thread(mMLWrapper).start();



        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mSensorModule.onDestroy();
        mMLWrapper.onDestroy();
        mStorageModule.onDestroy();
    }
}
