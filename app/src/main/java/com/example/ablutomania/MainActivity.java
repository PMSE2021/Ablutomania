package com.example.ablutomania;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.ablutomania.bgrecorder.RecorderService;

import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = MainActivity.class.getName();
    private static final String EXT_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int PERMISSION_REQUEST_ID = 0xeffe;

    private TextView mTextViewRotationVector;
    private TextView mTextViewAccelerometer;
    private TextView mTextViewGyroscope;
    private TextView mTextViewMagnetometer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        /* ask for runtime permission to save file on sdcard */
        if (!allowed(EXT_STORAGE))
            reqPerm(EXT_STORAGE);
        else {
            Intent intent = new Intent(this, RecorderService.class);
            startService(intent);
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_ID)
            return;

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
            return;

        startService(new Intent(this, RecorderService.class));
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