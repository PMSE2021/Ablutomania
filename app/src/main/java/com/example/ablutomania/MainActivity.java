package com.example.ablutomania;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.ablutomania.bgprocess.BackgroundService;

//import org.tensorflow.lite.Interpreter;

public class MainActivity extends Activity /* implements RecorderService.RecorderServiceListener */ {

    private static final String TAG = MainActivity.class.getName();

   // Interpreter tflite;
    private static Intent intentRecorder = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(intentRecorder == null) {
            intentRecorder = new Intent(this, BackgroundService.class);
            startService(intentRecorder);
        }

        // exit button to close app
        findViewById(R.id.btn_exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

/*
    public float doInference(String inputString) {
        //Input shape is ???
        float[] inputVal = new float[1];
        inputVal[0] = Float.valueOf(inputString);

        //Output shape is [1][3]
        float[][] outputval = new float[1][3];

        // Run interference passing the input shape & getting the output shape
        tflite.run(inputVal, outputval);
        // Inferred value is at [0][0]
        float inferredValue = outputval[0][0];

        return inferredValue;

    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("CNN_model_ablutomania-20-1 IH.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getDeclaredLength();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);
    }*/
}

