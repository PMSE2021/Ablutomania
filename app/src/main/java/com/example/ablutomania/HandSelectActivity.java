package com.example.ablutomania;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class HandSelectActivity extends Activity {

    private static final String TAG = "Ablutomania Log";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hand_select);

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
                Log.i(TAG, "CLicked on button left hand");
                Toast.makeText(HandSelectActivity.this, "App support right hand only! Please wear your watch on the right wrist", Toast.LENGTH_LONG).show();

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

}