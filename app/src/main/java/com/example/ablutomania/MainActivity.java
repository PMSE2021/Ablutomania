package com.example.ablutomania;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.ablutomania.bgrecorder.RecorderService;

import static com.example.ablutomania.bgrecorder.RecorderService.RecorderServiceListener;
import static com.example.ablutomania.bgrecorder.RecorderService.State;

public class MainActivity extends Activity implements RecorderServiceListener {

    private static final String TAG = MainActivity.class.getName();

    private RecorderService mRecorderService;
    private Button btnCtlRecorder;
    private TextView mProgressBarText;

    private boolean mIsBound;

    private static Intent intentRecorder = null;

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

        if(intentRecorder == null) {
            intentRecorder = new Intent(this, RecorderService.class);
        }

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
}