package com.execom.ljmocic.pushnotifications.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.execom.ljmocic.pushnotifications.MainActivity;
import com.execom.ljmocic.pushnotifications.R;
import com.google.android.gms.gcm.GcmListenerService;

public class PushListenerService extends GcmListenerService {

    private static final String LOG_TAG = PushListenerService.class.getSimpleName();

    // Gets data from received Bundle, and displays notification
    @Override
    public void onMessageReceived(final String from, final Bundle data) {
        String message = getMessage(data);
        displayNotification(message);
    }

    // Checks if message is default or contains JSON packed with data
    public static String getMessage(Bundle data) {
        return data.containsKey("default") ? data.getString("default") : data.getString("message", "");
    }

    // Sets up notification and handles intent
    private void displayNotification(final String message) {

        Intent notificationIntent = new Intent(this, MainActivity.class);

        // retrieves the old activity if it running in background
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int requestID = (int) System.currentTimeMillis();
        PendingIntent contentIntent = PendingIntent.getActivity(this, requestID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setSmallIcon(
                R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.message_received))
                .setContentText(message)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(Notification.PRIORITY_HIGH);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }

}
