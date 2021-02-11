package com.example.ablutomania.bgprocess.types;

import java.util.Arrays;

/*
 * Represents a datapoint with data if all used sensors
 */
public class Datapoint {
    private float[] rotationVectorData;
    private float[] gyroscopeData;
    private float[] accelerometerData;
    private float[] magnetometerData;
    private float[] mlResult;

    public Datapoint() {
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

    @Override
    public String toString() {
        return "Datapoint{" +
                "rotationVectorData=" + Arrays.toString(rotationVectorData) +
                ", gyroscopeData=" + Arrays.toString(gyroscopeData) +
                ", accelerometerData=" + Arrays.toString(accelerometerData) +
                ", magnetometerData=" + Arrays.toString(magnetometerData) +
                ", mlResult=" + Arrays.toString(mlResult) +
                '}';
    }
}