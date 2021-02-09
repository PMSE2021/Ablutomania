package com.example.ablutomania.bgprocess;

import java.util.LinkedList;

public class DataPipeline {

    private static FIFO<Datapoint> mInputFIFO = new FIFO<>();   // Is used by sensor module
    private static FIFO<Datapoint> mOutputFIFO = new FIFO<>();  // Is used by storage module

    /*
     * Generic FIFO class
     */
    private static class FIFO<T> {
        private LinkedList<T> mBuffer = new LinkedList<>();

        public void put(T t) {
            mBuffer.add(t);
        }

        public T get() {
            return (!mBuffer.isEmpty()) ? mBuffer.removeFirst() : null;
        }
    }

    /*
     * Represents a datapoint with data if all used sensors
     */
    public class Datapoint {
        private float[] rotationVectorData;
        private float[] gyroscopeData;
        private float[] accelerometerData;
        private float[] magnetometerData;
        private float[] mlResult;

        Datapoint() {
            rotationVectorData = null;
            gyroscopeData = null;
            accelerometerData = null;
            magnetometerData = null;
            mlResult = null;
        }

        public void setRotationVectorData(float[] data) {
            rotationVectorData = data;
        }

        public float[] getRotationVectorData() {
            return rotationVectorData;
        }

        public void setGyroscopeData(float[] data) {
            gyroscopeData = data;
        }

        public float[] getGyroscopeData() {
            return gyroscopeData;
        }

        public void setAccelerometerData(float[] data) {
            accelerometerData = data;
        }

        public float[] getAccelerometerData() {
            return accelerometerData;
        }

        public void setMagnetometerData(float[] data) {
            magnetometerData = data;
        }

        public float[] getMagnetometerData() {
            return magnetometerData;
        }

        public void setMlResult(float[] data) {
            mlResult = data;
        }

        public float[] getMlResult() {
            return mlResult;
        }
    }

}
