package com.example.ablutomania.bgprocess.mlmodel;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.ablutomania.bgprocess.DataPipeline;
import com.example.ablutomania.bgprocess.types.Datapoint;
import com.example.ablutomania.bgprocess.types.FIFO;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MLWrapper extends Activity implements Runnable {
    private static final String TAG = "MLWrapper";
    private static final double RATE = 50.;
    private static final int ML_BUFFER_SIZE = 1000;
    private Handler handler;
    private ByteBuffer mBuf;
    private long mLastTimestamp = -1;
    private boolean bMLprocessing = false;
    private Interpreter interpreter;

    /* Mutex for these variables are necessary - Just one thread should process the data*/
    private FIFO<Datapoint> mDpFIFO = new FIFO<>();
    private float[][][][] mMLInputBuffer = new float[1][ML_BUFFER_SIZE][13][1];
    private float[][] mMLOutputBuffer = new float[ML_BUFFER_SIZE][13];


    public MLWrapper() {
        handler = new Handler(Looper.getMainLooper());
        try {
            Interpreter interpreter = new Interpreter(loadModelFile());
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
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
                //TODO: Following exception is thrown here:
                //AndroidRuntime: java.lang.NullPointerException: Attempt to invoke virtual method 'void org.tensorflow.lite.Interpreter.run(java.lang.Object, java.lang.Object)' on a null object reference
                //at com.example.ablutomania.bgprocess.mlmodel.MLWrapper$1.run(MLWrapper.java:83)
                //at java.lang.Thread.run(Thread.java:764)
                try {
                    interpreter.run(mMLInputBuffer, mMLOutputBuffer);
                } catch (NullPointerException e) {
                    Log.e(TAG, e.getMessage());
                }
                Log.i(TAG, String.format("ML model finished."));

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
            float[] mlData = dp.getMlResult();

            //extract raw values from datapoint
            int idx = 0;
            for (int i = 0; i < (rotData.length-1) /* TODO: Here are just 4 features? */ ; i++, idx++) {
                mMLInputBuffer[1][numSamples][idx][1]= rotData[i];
            }
            for (int i = rotData.length; i < gyroData.length; i++, idx++) {
                mMLInputBuffer[1][numSamples][idx][1] = gyroData[i];
            }
            for (int i = 0; i < accelData.length; i++, idx++) {
                mMLInputBuffer[1][numSamples][idx][1] = accelData[i];
            }
            for (int i = 0; i < magData.length; i++, idx++) {
                mMLInputBuffer[1][numSamples][idx][1] = magData[i];
            }
            numSamples++;

        } while(numSamples < ML_BUFFER_SIZE);
    }

    private void postProcessData() {
        Datapoint dp;
        FIFO<Datapoint> mOutputFifo = DataPipeline.getOutputFIFO();

        // Move data from internal ML FIFO to output buffer
        while(mDpFIFO.size() > 0) {
            dp = mDpFIFO.get();

            if(dp == null)
                continue;   //If datapoint is null, proceeed with next one

            //TODO: Add MLResult {-1, 0, 1} to specific datapoint
            mOutputFifo.put(dp);

            Log.i(TAG, String.format("OutputFIFO size: %d", mOutputFifo.size()));
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("CNN_model_ablutomania-20-1 IH.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getDeclaredLength();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

    }


}
