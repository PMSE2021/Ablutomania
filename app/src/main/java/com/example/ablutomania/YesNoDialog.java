package com.example.ablutomania;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class YesNoDialog extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_yes_no_dialog);

        TextView textView = findViewById(R.id.text_question);
        Intent intent = getIntent();
        textView.setText(intent.getStringExtra("question"));

        initButton(R.id.btn_yes, true);
        initButton(R.id.btn_no, false);
    }

    void initButton(int id, boolean answer) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.putExtra("answer", answer);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }
}
