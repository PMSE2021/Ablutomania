package com.example.ablutomania;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "Ablutomania Log";

    private TextView mTextViewRotationVector;
    private TextView mTextViewAccelerometer;
    private TextView mTextViewGyroscope;
    private TextView mTextViewMagnetometer;
    private TextView mTextViewPressureSensor;
    private TextView mTextViewLightSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextViewRotationVector = findViewById(R.id.rotationvector);
        mTextViewAccelerometer  = findViewById(R.id.accelerometer);
        mTextViewGyroscope      = findViewById(R.id.gyroscope);
        mTextViewMagnetometer   = findViewById(R.id.magnetometer);
        mTextViewPressureSensor = findViewById(R.id.pressuresensor);
        mTextViewLightSensor    = findViewById(R.id.lightsensor);

        SensorManager mSensorManager    = ((SensorManager)getSystemService(SENSOR_SERVICE));
        Sensor mRotationSensor          = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor mAccelerometerSensor     = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mGyroscopeSensor         = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor mMagnetometerSensor      = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor mPressureSensor          = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        Sensor mLightSensor             = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        mSensorManager.registerListener(this, mAccelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGyroscopeSensor,     SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagnetometerSensor,  SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mPressureSensor,      SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mLightSensor,         SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            String msg = String.format(Locale.ENGLISH, "Rotation Vector:\nX: %.1f  Y: %.1f  Z: %.1f", event.values[0], event.values[1], event.values[2]);
            mTextViewRotationVector.setText(msg);
            Log.d(TAG, msg);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            String msg = String.format(Locale.ENGLISH, "Accelerometer (m/s\u00B2):\nX: %.2f  Y: %.2f  Z: %.2f", event.values[0], event.values[1], event.values[2]);
            mTextViewAccelerometer.setText(msg);
            Log.d(TAG, msg);
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            String msg = String.format(Locale.ENGLISH, "Gyroscope (rad/s):\nX: %.2f  Y: %.2f  Z: %.2f", event.values[0], event.values[1], event.values[2]);
            mTextViewGyroscope.setText(msg);
            Log.d(TAG, msg);
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            String msg = String.format(Locale.ENGLISH, "Magnetic field (\u00B5/T):\nX: %.1f  Y: %.1f  Z: %.1f", event.values[0], event.values[1], event.values[2]);
            mTextViewMagnetometer.setText(msg);
            Log.d(TAG, msg);
        }
        else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            String msg = String.format(Locale.ENGLISH, "Pressure Sensor:\n%.1f hPa", event.values[0]);
            mTextViewPressureSensor.setText(msg);
            Log.d(TAG, msg);
        }
        else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            String msg = String.format(Locale.ENGLISH, "Light Sensor:\n%.1f lux", event.values[0]);
            mTextViewLightSensor.setText(msg);
            Log.d(TAG, msg);
        }
        else
            Log.d(TAG, "Unknown sensor type");
    }
}