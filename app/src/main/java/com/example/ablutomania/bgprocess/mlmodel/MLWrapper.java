package com.example.ablutomania.bgprocess.mlmodel;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.ablutomania.bgprocess.DataPipeline;
import com.example.ablutomania.bgprocess.types.Datapoint;
import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import java.nio.ByteBuffer;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MLWrapper extends Activity implements Runnable {
    private static final String TAG = "MLWrapper";
    private static final double RATE = 50.;
    private Handler handler;
    private ByteBuffer mBuf;
    private long mLastTimestamp = -1;

    Interpreter tflite;

    public MLWrapper() {
        handler = new Handler(Looper.getMainLooper());
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        // TODO: Function is now called cyclic, but not in a period of 10ms
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
        if (DataPipeline.getInputFIFO().size() < 1)
            return;
        else {
            //Get SensorValues from Buffer
            Datapoint dp = DataPipeline.getInputFIFO().get();

            // get data from data pipeline
            float[] rotData = dp.getRotationVectorData();
            float[] gyroData = dp.getGyroscopeData();
            float[] accelData = dp.getAccelerometerData();
            float[] magData = dp.getMagnetometerData();
            float[] mlData = dp.getMlResult();


            // check if data pipeline is empty or filed
            if (dp == null)
                return;

            // Send data to ML-Runner
           mlData[0] = doInference(rotData, gyroData, accelData, magData);

            // TODO: Output-Buffer with DataPipeline.getOutputFIFO().put(???);

        }

    }

    public float doInference(float[] rotData,  float[] gyroData, float[] accelData, float[] magData) {

        //Input shape is 1000x13+???
        float[][] inputVal = new float[1000][rotData.length + gyroData.length + accelData.length + magData.length];
        for(int  j = 0; j < 1000; j++) {
            //TODO: Update Data during for
            for (int i = 0; i < rotData.length; i++) {
                inputVal[j][i] = rotData[i];
            }
            for (int i = rotData.length; i < (rotData.length + gyroData.length); i++) {
                inputVal[j][i] = rotData[i];
            }
            for (int i = (rotData.length + gyroData.length); i < (rotData.length + gyroData.length + accelData.length); i++) {
                inputVal[j][i] = rotData[i];
            }
            for (int i = (rotData.length + gyroData.length + accelData.length); i < (rotData.length + gyroData.length + accelData.length + magData.length); i++) {
                inputVal[j][i] = rotData[i];
            }
        }

        //Output shape is 1
        float[] outputval = new float[1];
        // Run interference passing the input shape & getting the output shape
        tflite.run(inputVal, outputval);
        // return Output {-1,0,1}
        return outputval[0];

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
