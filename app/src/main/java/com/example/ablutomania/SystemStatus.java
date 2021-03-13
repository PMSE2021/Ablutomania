package com.example.ablutomania;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.ablutomania.watchface.ComplicationBroadcastReceiver;

import java.util.HashMap;
import java.util.Map;

public class SystemStatus {

    private static final SystemStatus instance = new SystemStatus();

    public static final String STATUS_UPDATE = "com.example.ablutomania.STATUS_UPDATE";

    private SystemStatus() {}

    public enum Status {

        OK(1),
        WARNING(2),
        ERROR(3);

        private int value;

        private static Map map = new HashMap<>();

        private Status(int value) {
            this.value = value;
        }

        static {
            for (Status status : Status.values()) {
                map.put(status.value, status);
            }
        }

        public static Status valueOf(int status) {
            return (Status) map.get(status);
        }

        public int getValue() {
            return value;
        }
    }

    private Status status = Status.OK;

    public static SystemStatus GetInstance() {
        return instance;
    }

    public void setStatusError(Context context) {
        setStatusAndNotify(context, Status.ERROR);
    }

    public void setStatusWarning(Context context) {
        if (this.status == Status.OK)
            setStatusAndNotify(context, Status.WARNING);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    private void setStatusAndNotify(Context context, Status status) {
        this.status = status;
        Intent intent = new Intent(context, ComplicationBroadcastReceiver.class);
        intent.setAction(SystemStatus.STATUS_UPDATE);
        intent.putExtra("status", status.getValue());
        context.sendBroadcast(intent);
    }
}
