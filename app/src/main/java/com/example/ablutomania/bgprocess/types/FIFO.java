package com.example.ablutomania.bgprocess.types;

import java.util.LinkedList;

/*
 * Generic FIFO class
 */
public class FIFO<T> {
    private LinkedList<T> mBuffer = new LinkedList<>();

    public void put(T t) { mBuffer.add(t); }

    public T get() {
        return (!mBuffer.isEmpty()) ? mBuffer.removeFirst() : null;
    }

    public int size() { return mBuffer.size(); }

    public void clear() { mBuffer.clear(); }
}