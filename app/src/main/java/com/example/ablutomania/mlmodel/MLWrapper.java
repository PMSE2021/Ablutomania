package com.example.ablutomania.mlmodel;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;

public class MLWrapper {
    protected LinkedList<File> mFiles;
    protected HashMap<Integer,OutputStream> mStreams = new HashMap<>();

    public MLWrapper(LinkedList<File> files) {
        mFiles = files;
    }

    public OutputStream getOutputStream(int j) throws FileNotFoundException {
        OutputStream s = mStreams.get(j);
        if (s == null) {
            File f = mFiles.get(j);
            FileOutputStream fos = new FileOutputStream(f);
            f.delete();
            mStreams.put(j, new BufferedOutputStream(fos));
        }

        return mStreams.get(j);
    }


    /*
     *   // Usage:
     *   Datapoint dp = new Datapoint();
     *
     *   // Set accelerometer data
     *   dp.afAccelerometerData = new float[] {1.f, 0.2f, -5.3f};
     *   // Get accelerometer data
     *   float[] accelDataOut = dp.afAccelerometerData;
     *
     */

    public class Datapoint {
        private float[] afRotationVectorData;
        private float[] afGyroscopeData;
        public float[] afAccelerometerData;
        private float[] afMagnetometerData;
        private float[] fMLResult;
        private int iTimestamp;

        Datapoint() { /* not used */}
    }

}
