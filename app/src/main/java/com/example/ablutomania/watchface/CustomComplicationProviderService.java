package com.example.ablutomania.watchface;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationManager;
import android.support.wearable.complications.ComplicationProviderService;
import android.support.wearable.complications.ComplicationText;
import android.util.Log;

import com.example.ablutomania.SystemStatus;

public class CustomComplicationProviderService extends ComplicationProviderService {

    private static final String TAG = "ComplicationProvider";

    @Override
    public void onComplicationActivated(
            int complicationId, int dataType, ComplicationManager complicationManager) {
        Log.e(TAG, "onComplicationActivated(): " + complicationId);
    }

    @Override
    public void onComplicationUpdate(
            int complicationId, int dataType, ComplicationManager complicationManager) {
        Log.e(TAG, "onComplicationUpdate() id: " + complicationId);
        //Used to create a unique key to use with SharedPreferences for this complication.
        ComponentName thisProvider = new ComponentName(this, getClass());

        PendingIntent complicationPendingIntent =
                ComplicationBroadcastReceiver.getToggleIntent(
                        this, thisProvider, complicationId);


        String status = SystemStatus.GetInstance().getStatus().toString();

        //String status = SystemStatus.STATUS_UPDATE;

        Log.e(TAG, SystemStatus.GetInstance().getStatus().toString());

        ComplicationData complicationData = null;

        if (dataType == ComplicationData.TYPE_LONG_TEXT) {
            complicationData =
                    new ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                            .setLongText(ComplicationText.plainText("App Status: " + status))
                            .setTapAction(complicationPendingIntent)
                            .build();
        } else {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Unexpected complication type " + dataType);
            }
        }

        if (complicationData != null) {
            complicationManager.updateComplicationData(complicationId, complicationData);

        } else {
            // If no data is sent, we still need to inform the ComplicationManager, so the update
            // job can finish and the wake lock isn't held any longer than necessary.
            complicationManager.noUpdateRequired(complicationId);
        }
    }

    @Override
    public void onComplicationDeactivated(int complicationId) {
        Log.d(TAG, "onComplicationDeactivated(): " + complicationId);
    }
}
