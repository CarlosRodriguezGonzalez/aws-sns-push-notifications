package com.execom.ljmocic.pushnotifications;

import android.app.Application;
import android.util.Log;

import com.execom.ljmocic.pushnotifications.aws.AWSMobileClient;

public class PushNotificationApplication extends Application {

    private static final String LOG_TAG = Application.class.getName();

    @Override
    public void onCreate(){
        super.onCreate();
        AWSMobileClient.initializeMobileClientIfNecessary(getApplicationContext());
    }

}
