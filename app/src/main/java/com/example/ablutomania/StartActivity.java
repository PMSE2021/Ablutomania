package com.example.ablutomania;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class StartActivity extends Activity {

    private static final String TAG = "Ablutomaina Log";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        StartButton();
        ExitButton();
    }
    private void StartButton(){
        //Button to enter menu
        Button btn_start_application = findViewById(R.id.btn_start_application);
        btn_start_application.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toast.makeText(StartActivity.this, "Let's start", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Clicked on start button");
                startActivity(new Intent(StartActivity.this, HandSelectActivity.class));
            }
        });

    }
    private void ExitButton(){
        //Exit application
        Button btn_exit_application = findViewById(R.id.btn_exit_application);
        btn_exit_application.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Clicked on exit button");
                finish();
            }
        });

    }
}