package com.example.ablutomania;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.logging.LogManager;

public class SystemStatus {

    private static final SystemStatus instance = new SystemStatus();

    public static final String STATUS_UPDATE = "com.example.ablutomania.STATUS_UPDATE";

    private SystemStatus() {}

    public enum Status {
        OK,
        WARNING,
        ERROR
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
        Intent intent = new Intent(SystemStatus.STATUS_UPDATE);
        intent.putExtra("status", status);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
