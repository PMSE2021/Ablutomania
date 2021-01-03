package com.example.ablutomania;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;

public class StartActivity extends WearableActivity {

    private static final String TAG = "Ablutomania Log";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        StartButton();
        ExitButton();

        // Enables Always-on
        setAmbientEnabled();
    }
    private void StartButton(){
        //Button to enter menu
        Button btn_start_application = findViewById(R.id.btn_start_application);
        btn_start_application.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toast.makeText(StartActivity.this, "Let's start", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(StartActivity.this, MainActivity.class));
            }
        });

    }
    private void ExitButton(){
        //Exit application
        Button btn_exit_application = findViewById(R.id.btn_exit_application);
        btn_exit_application.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }
}