package com.example.ablutomania;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class HandSelectActivity extends Activity {

    private static final String TAG = "Ablutomania Log";
    private static final String EXT_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int PERMISSION_REQUEST_ID = 0xeffe;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hand_select);

        /* ask for runtime permission to save recorded file on sdcard */
        if (!allowed(EXT_STORAGE))
            reqPerm(EXT_STORAGE);
        else
            createView();

    }

    private void createView() {
        LeftHandButton();
        RightHandButton();
        BackStartButton();
    }

    private void LeftHandButton() {
        // Button: select left hand
        Button btn_left_hand = findViewById(R.id.btn_left_hand);
        btn_left_hand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Clicked on button left hand");
                Toast.makeText(HandSelectActivity.this, "App supports right hand only! Please wear your watch on the right wrist", Toast.LENGTH_LONG).show();

            }
        });
    }
    private void RightHandButton() {
        // Button: select right hand
        Button btn_right_hand = findViewById(R.id.btn_right_hand);
        btn_right_hand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "CLicked on button right hand");
                Toast.makeText(HandSelectActivity.this, "Right hand selected", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(HandSelectActivity.this, MainActivity.class));
            }
        });
    }
    private void BackStartButton(){
        // Button: back to start menu
        Button btn_back_start = findViewById(R.id.btn_back_start);
        btn_back_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (   (requestCode != PERMISSION_REQUEST_ID)
            || (grantResults[0] != PackageManager.PERMISSION_GRANTED) )
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });

        createView();
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