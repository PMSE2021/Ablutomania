package com.example.ablutomania;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.ablutomania.bgprocess.BackgroundService;

//import org.tensorflow.lite.Interpreter;

public class MainActivity extends Activity /* implements RecorderService.RecorderServiceListener */ {

    private static final String TAG = MainActivity.class.getName();
    private static final String EXT_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int PERMISSION_REQUEST_ID = 0xeffe;

    private static Intent intentRecorder = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /** ask for runtime permission to save file on sdcard */
        if (!allowed(EXT_STORAGE))
            reqPerm(EXT_STORAGE);
        else {
            initApp();

    /*        new Handler().post(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }); */
        }

/*
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
 */
    }

    private void initApp() {
        if(intentRecorder == null) {
            intentRecorder = new Intent(this, BackgroundService.class);
            startService(intentRecorder);
        }

        // exit button to close app
        findViewById(R.id.btn_exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        getUserFeedback();
    }

    void getUserFeedback() {
        Intent intent = new Intent(this, YesNoDialog.class);
        intent.putExtra("question", getString(R.string.ml_user_feedback));
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "User response: " + data.getBooleanExtra("answer", false));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }



    /**
     * down here is only permission handling stuff
     */

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_ID)
            return;

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
            return;

        initApp();
    }

    private boolean allowed(String perm) {
        return ContextCompat.checkSelfPermission(this,perm)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void reqPerm(String perm) {
        ActivityCompat.requestPermissions(this,
                new String[]{perm},
                PERMISSION_REQUEST_ID);
    }
}

