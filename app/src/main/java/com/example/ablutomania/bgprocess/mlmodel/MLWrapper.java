package com.example.ablutomania.bgprocess.mlmodel;

import android.os.Handler;
import android.util.Log;

public class MLWrapper implements Runnable {
    private static final String TAG = "MLWrapper";
    private Handler handler;
    private long mLastTimestamp = -1;

    public MLWrapper() {
        handler = new Handler();
    }

    @Override
    public void run() {
        // TODO: Function is now called cyclic, but not in a period of 10ms
        handler.postDelayed(this, 1);

        long mOffset = 0;
        if (mLastTimestamp == -1) {
            mLastTimestamp = System.currentTimeMillis();
        } else {
            long t = System.currentTimeMillis();
            mOffset = t - mLastTimestamp;
            mLastTimestamp = t;
        }

        Log.d(TAG, String.format("running : offset = %dms", mOffset));

        // put code here..
    }
}
