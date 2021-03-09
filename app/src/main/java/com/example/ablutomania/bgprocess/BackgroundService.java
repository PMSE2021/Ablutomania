package com.example.ablutomania.bgprocess;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

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

//        Log.d(TAG, "onStart: " + intent.toString());

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
