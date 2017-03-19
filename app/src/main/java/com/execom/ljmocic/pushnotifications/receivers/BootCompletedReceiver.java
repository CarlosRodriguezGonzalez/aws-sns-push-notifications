package com.execom.ljmocic.pushnotifications.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.execom.ljmocic.pushnotifications.services.PushListenerService;

public class BootCompletedReceiver extends BroadcastReceiver {

    // Gets notified when device is booted, then starts service
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            Intent pushIntent = new Intent(context, PushListenerService.class);
            context.startService(pushIntent);
        }

    }

}

