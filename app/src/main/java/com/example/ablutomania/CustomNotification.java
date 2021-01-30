package com.example.ablutomania;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.os.Build;
import android.util.Log;

public abstract class CustomNotification extends Service {

    public static final int NOTIFICATION_ID = 0x008;

    public static Notification updateNotification(Context ctx, String channelId, String content) {
        Log.e("cusnotify", "update notification: " + content);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);

        Notification.Builder nb = new Notification.Builder(ctx, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notification_title));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create notification channel with unique ID CHANID
            NotificationChannel channel = new NotificationChannel(channelId,
                    ctx.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            // Submit the notification channel object to the notification manager
            nm.createNotificationChannel(channel);
        }

        /*
         * update the notification text accordingly
         */
        nb.setContentText(content);

        Notification n = nb.build();
            nm.notify(NOTIFICATION_ID, n);
            return n;
    }

}
