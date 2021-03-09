package com.example.ablutomania.bgprocess.mlmodel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.ablutomania.R;
import com.example.ablutomania.YesNoDialog;
import com.example.ablutomania.bgprocess.DataPipeline;
import com.example.ablutomania.bgprocess.types.Datapoint;
import com.example.ablutomania.bgprocess.types.FIFO;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

public class MLWrapper extends Activity implements Runnable {
    private static final String TAG = "MLWrapper";
    private static final double RATE = 50.;
    private static final int ML_BUFFER_SIZE = 500;
    private Handler handler;
    private ByteBuffer mBuf;
    private long mLastTimestamp = -1;
    private boolean bMLprocessing = false;
    private static final String MODEL_PATH = "CNN_LSTM_model_ablutomania.tflite";
    private Interpreter tflite;
    private Context ctx;
    private Boolean wasCompulsiveHandwashing = null;

    private static final int NO_HANDWASHING = 0;
    private static final int HANDWASHING = 1;
    private static final int COMPULSIVE_HANDWASHING = -1;

    /* Mutex for these variables are necessary - Just one thread should process the data*/
    private FIFO<Datapoint> mDpFIFO = new FIFO<>();
    private float[][][][] mMLInputBuffer = new float[1][ML_BUFFER_SIZE][13][1];
    private float[][] mMLOutputBuffer = new float[1][3];
    private int mLResult = 0;

    public MLWrapper(Context context) {
        this.ctx = context;
        handler = new Handler(Looper.getMainLooper());


        try {
            tflite = new Interpreter(loadModelFile());

        } catch (Exception e) {
            Log.e(TAG, "Interpreter: "+e.getMessage());
        }
    }

    @Override
    public void run() {
        // TODO: Function is now called cyclic, but not in the specified period
        handler.postDelayed(this, (long) (1e3 / RATE));

        long mOffset = 0;
        if (mLastTimestamp == -1) {
            mLastTimestamp = System.currentTimeMillis();
        } else {
            long t = System.currentTimeMillis();
            mOffset = t - mLastTimestamp;
            mLastTimestamp = t;
        }
        Log.d(TAG, String.format("running: offset = %dms", mOffset));

        //No SensorData -> return
        if (DataPipeline.getInputFIFO().size() < ML_BUFFER_SIZE)
            return;

        synchronized (MLWrapper.this) {
            if(bMLprocessing)
                return; //Return if ML is still processing previous data

            bMLprocessing = true;
        }

        /*
         * Create own thread for ML task
         */
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Do some pre-processing of data
                preProcessData();

                Log.i(TAG, String.format("Start running ML model.."));
                // Run interference passing the input shape & getting the output shape
                try {
                    tflite.run(mMLInputBuffer, mMLOutputBuffer);
                    Log.i(TAG, String.format("ML model finished."));

                } catch (NullPointerException e) {
                    Log.e(TAG, e.toString());
                    Log.i(TAG, String.format("ML model failed"));
                }

                // Post-process data here
                postProcessData();

                synchronized (MLWrapper.this) {
                    bMLprocessing = false;
                }
            }
        }).start();
    }

    public void onDestroy() { super.onDestroy(); }

    private void preProcessData() {
        Datapoint dp;
        FIFO<Datapoint> mInputFifo = DataPipeline.getInputFIFO();
        int numSamples = 0;

        do {
            Log.i(TAG, String.format("InputFIFO size: %d", mInputFifo.size()));

            //Get SensorValues from input FIFO
            dp = mInputFifo.get();

            // check if data pipeline is empty or filed
            if (dp == null)
                return;

            //Put datapoints in a FIFO to store datapoint temporary and to be able to still have
            //the raw data after ML task has finished
            mDpFIFO.put(dp);

            // get data from data pipeline
            float[] rotData = dp.getRotationVectorData();
            float[] gyroData = dp.getGyroscopeData();
            float[] accelData = dp.getAccelerometerData();
            float[] magData = dp.getMagnetometerData();

            //extract raw values from datapoint
            int idx = 0;

            if(null != rotData)
                for (int i = 0; i < (rotData.length - 1) /* TODO: Here are just 4 features? */ ; i++, idx++)
                    mMLInputBuffer[0][numSamples][idx][0] = rotData[i];

            if(null != gyroData)
                for (int i = 0; i < gyroData.length; i++, idx++)
                    mMLInputBuffer[0][numSamples][idx][0] = gyroData[i];

            if(null != accelData)
                for (int i = 0; i < accelData.length; i++, idx++)
                    mMLInputBuffer[0][numSamples][idx][0] = accelData[i];

            if(null != magData)
                for (int i = 0; i < magData.length; i++, idx++)
                    mMLInputBuffer[0][numSamples][idx][0] = magData[i];

            numSamples++;
        } while(numSamples < ML_BUFFER_SIZE);
    }

    private void postProcessData() {
        Datapoint dp;
        FIFO<Datapoint> mOutputFifo = DataPipeline.getOutputFIFO();

        //Convert result
        if(mMLOutputBuffer[0][0] == 0 && mMLOutputBuffer[0][1] == 0 && mMLOutputBuffer[0][2] == 1) mLResult = COMPULSIVE_HANDWASHING;
        if(mMLOutputBuffer[0][0] == 0 && mMLOutputBuffer[0][1] == 1 && mMLOutputBuffer[0][2] == 0) mLResult = NO_HANDWASHING;
        if(mMLOutputBuffer[0][0] == 1 && mMLOutputBuffer[0][1] == 0 && mMLOutputBuffer[0][2] == 0) mLResult = HANDWASHING;
        Log.i(TAG, String.format("mLResult is " ));
        Log.i(TAG, Float.toString(mLResult));

        if(NO_HANDWASHING != mLResult) {
            /* Ask user if handwashing was compulsive */
            //getUserFeedback();

            //TODO: Synchronize and timeout
            if (null != wasCompulsiveHandwashing) {
                mLResult = (true == wasCompulsiveHandwashing) ? COMPULSIVE_HANDWASHING : HANDWASHING;
                wasCompulsiveHandwashing = null;
            }
        }

        // Move data from internal ML FIFO to output buffer
        while(mDpFIFO.size() > 0) {
            dp = mDpFIFO.get();

            if(dp == null)
                continue;   //If datapoint is null, proceeed with next one

            dp.setMlResult(new float[] { mLResult });
            mOutputFifo.put(dp);

            Log.i(TAG, String.format("OutputFIFO size: %d", mOutputFifo.size()));
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = ctx.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    void getUserFeedback() {
        Intent intent = new Intent(this, YesNoDialog.class);
        intent.putExtra("question", getString(R.string.ml_user_feedback));
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        wasCompulsiveHandwashing = data.getBooleanExtra("answer", false);
    }
}