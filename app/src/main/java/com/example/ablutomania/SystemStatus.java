package com.example.ablutomania;

public class SystemStatus {

    private static final SystemStatus instance = new SystemStatus();

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

    public void setStatusError() {
        this.status = Status.ERROR;
    }

    public void setStatusWarning() {
        if (this.status == Status.OK)
            this.status = Status.WARNING;
    }

    public Status getStatus() {
        return status;
    }
}
