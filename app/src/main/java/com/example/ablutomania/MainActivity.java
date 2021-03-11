package com.example.ablutomania;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
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


        /** some Huawei fuckup hackery, see
         * https://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it
         */
        final SharedPreferences sp = getSharedPreferences("ProtectedApps", Context.MODE_PRIVATE);
        if("huawei".equalsIgnoreCase(android.os.Build.MANUFACTURER) && !sp.getBoolean("protected",false)) {
            AlertDialog.Builder builder  = new AlertDialog.Builder(this);
            builder.setTitle(R.string.huawei_headline).setMessage(R.string.huawei_text)
                    .setPositiveButton(R.string.go_to_protected, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent();
                            intent.setComponent(new ComponentName("com.huawei.systemmanager",
                                    "com.huawei.systemmanager.optimize.process.ProtectActivity"));
                            startActivity(intent);
                            sp.edit().putBoolean("protected",true).commit();
                        }
                    }).create().show();
        }
    }

    private void initApp() {
        if(intentRecorder == null) {
            intentRecorder = new Intent(this, BackgroundService.class);
            startService(intentRecorder);
        }

        // exit button to close app
        findViewById(R.id.btn_exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(null != intentRecorder) {
                    stopService(intentRecorder);
                    intentRecorder = null;
                }
                finish();
            }
        });
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

