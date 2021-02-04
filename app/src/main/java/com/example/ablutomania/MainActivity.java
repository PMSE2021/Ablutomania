package com.example.ablutomania;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.tensorflow.lite.Interpreter;

import androidx.annotation.Nullable;

import com.example.ablutomania.bgrecorder.RecorderService;

import static com.example.ablutomania.bgrecorder.RecorderService.RecorderServiceListener;
import static com.example.ablutomania.bgrecorder.RecorderService.State;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

public class MainActivity extends Activity implements RecorderServiceListener {

    private static final String TAG = MainActivity.class.getName();

    private RecorderService mRecorderService;
    private Button btnCtlRecorder;
    private TextView mProgressBarText;

    private boolean mIsBound;

    private static Intent intentRecorder = null;

    Interpreter tflite;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has
            // been established, giving us the service object we can use
            // to interact with the service.
            mRecorderService = ((RecorderService.LocalBinder)service).getService();
            mRecorderService.setListener(MainActivity.this);

            SetRecordingButton();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has
            // been unexpectedly disconnected -- that is, its process
            // crashed. Because it is running in the same process, this
            // should never happen.
            mRecorderService.removeListener(MainActivity.this);
            mRecorderService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCtlRecorder = findViewById(R.id.btnCtlRecorder);
        mProgressBarText = findViewById(R.id.textStatusRecorder);

        // Create the tflite object, loaded from the model file
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (Exception ex){
            ex.printStackTrace();
        }

        if(intentRecorder == null) {
            intentRecorder = new Intent(this, RecorderService.class);
        }


        SensorManager mSensorManager    = ((SensorManager)getSystemService(SENSOR_SERVICE));
        Sensor mRotationSensor          = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor mAccelerometerSensor     = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mGyroscopeSensor         = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor mMagnetometerSensor      = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if(!mIsBound) { doBindService(); }

        BackButton();
    }

    private void doBindService() {
        bindService(intentRecorder,
                mConnection,
                MainActivity.BIND_AUTO_CREATE);
                mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach existing connection
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    private void SetRecordingButton() {
        onStateChanged(mRecorderService.getState());

        btnCtlRecorder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(getString(R.string.btn_set_recorder_start) == btnCtlRecorder.getText()) {
                    if(!mIsBound) { doBindService(); }
                    // start recorder service
                    startService(intentRecorder);
                }
                else if(getString(R.string.btn_set_recorder_stop) == btnCtlRecorder.getText()) {
                    // before stopping, it is necessary to unbind service first
                    doUnbindService();
                    // stop recorder service
                    stopService(intentRecorder);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    private void BackButton() {
        // back to hand select activity
        Button btn_back_main = findViewById(R.id.btn_back_main);
        btn_back_main.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Check if connection has unpredictably lost */
                if(mRecorderService != null) {
                    mRecorderService.removeListener(MainActivity.this);
                }
                finish();
            }
        });
    }

    @Override
    public void onStateChanged(State state) {
        try {
            switch (state) {
                case IDLE: {
                    btnCtlRecorder.setText(R.string.btn_set_recorder_start);
                    mProgressBarText.setText(R.string.notification_recording_paused);
                    break;
                }
                case PREPARING: {
                    mProgressBarText.setText(R.string.notification_recording_preping);
                    break;
                }
                case RECORDING: {
                    btnCtlRecorder.setText(R.string.btn_set_recorder_stop);
                    mProgressBarText.setText(R.string.notification_recording_ongoing);
                    break;
                }
                default: {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
    }
}

