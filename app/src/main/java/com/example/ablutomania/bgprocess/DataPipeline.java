package com.example.ablutomania.bgprocess;

import com.example.ablutomania.bgprocess.types.Datapoint;
import com.example.ablutomania.bgprocess.types.FIFO;

public class DataPipeline {

    private static FIFO<Datapoint> mInputFIFO = new FIFO<>();   // Is used by sensor module
    private static FIFO<Datapoint> mOutputFIFO = new FIFO<>();  // Is used by storage module

    public static FIFO<Datapoint> getInputFIFO() {
        return mInputFIFO;
    }

    public static FIFO<Datapoint> getOutputFIFO() {
        return mOutputFIFO;
    }
}
