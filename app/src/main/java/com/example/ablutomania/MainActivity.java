package com.example.ablutomania;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
//import org.tensorflow.lite.Interpreter;

import androidx.annotation.Nullable;

import com.example.ablutomania.bgrecorder.RecorderService;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = MainActivity.class.getName();

    private TextView mTextViewRotationVector;
    private TextView mTextViewAccelerometer;
    private TextView mTextViewGyroscope;
    private TextView mTextViewMagnetometer;
   // Interpreter tflite;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

/*
        // Create the tflite object, loaded from the model file
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (Exception ex){
            ex.printStackTrace();
        }*/

        mTextViewRotationVector = findViewById(R.id.rotationvector);
        mTextViewAccelerometer  = findViewById(R.id.accelerometer);
        mTextViewGyroscope      = findViewById(R.id.gyroscope);
        mTextViewMagnetometer   = findViewById(R.id.magnetometer);



        SensorManager mSensorManager    = ((SensorManager)getSystemService(SENSOR_SERVICE));
        Sensor mRotationSensor          = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor mAccelerometerSensor     = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mGyroscopeSensor         = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor mMagnetometerSensor      = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mSensorManager.registerListener(this, mRotationSensor,      SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mAccelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGyroscopeSensor,     SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagnetometerSensor,  SensorManager.SENSOR_DELAY_NORMAL);

        BackButton();

        startService( new Intent(this, RecorderService.class) );
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            String msg = String.format(Locale.ENGLISH, "Rotation Vector:\nX: %.1f  Y: %.1f  Z: %.1f", event.values[0], event.values[1], event.values[2]);
            mTextViewRotationVector.setText(msg);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            String msg = String.format(Locale.ENGLISH, "Accelerometer (m/s\u00B2):\nX: %.2f  Y: %.2f  Z: %.2f", event.values[0], event.values[1], event.values[2]);
            mTextViewAccelerometer.setText(msg);
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            String msg = String.format(Locale.ENGLISH, "Gyroscope (rad/s):\nX: %.2f  Y: %.2f  Z: %.2f", event.values[0], event.values[1], event.values[2]);
            mTextViewGyroscope.setText(msg);
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            String msg = String.format(Locale.ENGLISH, "Magnetic field (\u00B5/T):\nX: %.1f  Y: %.1f  Z: %.1f", event.values[0], event.values[1], event.values[2]);
            mTextViewMagnetometer.setText(msg);
        }
    }

    private void BackButton(){
        // back to hand select activity
        Button btn_back_main = findViewById(R.id.btn_back_main);
        btn_back_main.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
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

