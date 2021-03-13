package com.example.ablutomania.watchface;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.complications.ProviderUpdateRequester;
import android.util.Log;

import com.example.ablutomania.SystemStatus;

public class ComplicationBroadcastReceiver extends BroadcastReceiver {

    /**
     * Simple {@link BroadcastReceiver} subclass for asynchronously incrementing an integer for any
     * complication id triggered via TapAction on complication. Also, provides static method to create
     * a {@link PendingIntent} that triggers this receiver.
     */

        private static final String EXTRA_PROVIDER_COMPONENT =
                "com.example.android.wearable.watchface.provider.action.PROVIDER_COMPONENT";
        private static final String EXTRA_COMPLICATION_ID =
                "com.example.android.wearable.watchface.provider.action.COMPLICATION_ID";

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String action = intent.getAction();

            if(action != null && action.equals(SystemStatus.STATUS_UPDATE)) {
                SystemStatus.GetInstance().setStatus(SystemStatus.Status.values()[intent.getIntExtra("status", 0)]);
                return;

            }
            Log.e("ComplicationBroadcastReceiver", "receive broadcast message: " + action);

            ComponentName provider = extras.getParcelable(EXTRA_PROVIDER_COMPONENT);
            int complicationId = extras.getInt(EXTRA_COMPLICATION_ID);


            // Update data for complication.




            // Request an update for the complication that has just been tapped.
            ProviderUpdateRequester requester = new ProviderUpdateRequester(context, provider);
            requester.requestUpdate(complicationId);
        }

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        static PendingIntent getToggleIntent(
                Context context, ComponentName provider, int complicationId) {
            Intent intent = new Intent(context, ComplicationBroadcastReceiver.class);
            intent.putExtra(EXTRA_PROVIDER_COMPONENT, provider);
            intent.putExtra(EXTRA_COMPLICATION_ID, complicationId);

            // Pass complicationId as the requestCode to ensure that different complications get
            // different intents.
            return PendingIntent.getBroadcast(
                    context, complicationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        /**
         * Returns the key for the shared preference used to hold the current state of a given
         * complication.
         */
        static String getPreferenceKey(ComponentName provider, int complicationId) {
            return provider.getClassName() + complicationId;
        }
    }
