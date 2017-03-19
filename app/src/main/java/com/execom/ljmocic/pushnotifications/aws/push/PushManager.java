package com.execom.ljmocic.pushnotifications.aws.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.SetEndpointAttributesRequest;
import com.execom.ljmocic.pushnotifications.utils.ThreadUtils;

import java.util.HashMap;
import java.util.Map;

public class PushManager implements GCMTokenHelper.GCMTokenUpdateObserver {

    public interface PushStateListener {
        void onPushStateChange(PushManager pushManager, boolean isEnabled);
    }

    private static final String LOG_TAG = PushManager.class.getSimpleName();

    // Name of the shared preferences
    private static final String SHARED_PREFS_FILE_NAME = PushManager.class.getName();

    // Keys in shared preferences
    private static final String SHARED_PREFS_PUSH_ENABLED = "pushEnabled";
    private static final String SHARED_PREFS_KEY_ENDPOINT_ARN = "endpointArn";
    private static final String SHARED_PREFS_PREVIOUS_PLATFORM_APPLICATION = "previousPlatformApp";

    // Constants for SNS
    private static final String SNS_PROTOCOL_APPLICATION = "application";
    private static final String SNS_ENDPOINT_ATTRIBUTE_ENABLED = "Enabled";

    private static PushStateListener pushStateListener;

    private final AmazonSNS sns;

    private final SharedPreferences sharedPreferences;

    private final GCMTokenHelper gcmTokenHelper;

    private final String platformApplicationArn;
    private String endpointArn;
    private boolean shouldEnablePush;
    private boolean pushEnabled;
    private Boolean previousPushState = null;
    private final String defaultTopicArn;

    public PushManager(final Context context,
                       final GCMTokenHelper gcmTokenHelper,
                       final AWSCredentialsProvider provider,
                       final String platformApplicationArn,
                       final ClientConfiguration clientConfiguration,
                       final String defaultTopicArn,
                       final String[] topicArns,
                       final Regions region) {

        sharedPreferences = context.getSharedPreferences(SHARED_PREFS_FILE_NAME,
                Context.MODE_PRIVATE);

        this.gcmTokenHelper = gcmTokenHelper;
        this.platformApplicationArn = platformApplicationArn;
        this.defaultTopicArn = defaultTopicArn;
        sns = new AmazonSNSClient(provider, clientConfiguration);
        sns.setRegion(Region.getRegion(region));


        // Avoid the situation where a previous download/build of the sample app has
        // been run in a re-used emulator and the platform application arn changed.
        final String previousPlatformApp =
                sharedPreferences.getString(SHARED_PREFS_PREVIOUS_PLATFORM_APPLICATION, "");

        if (!previousPlatformApp.equalsIgnoreCase(platformApplicationArn)) {
            Log.d(LOG_TAG, "SNS platform application ARN changed or not set. Triggering SNS endpoint refresh.");
            endpointArn = "";
            // clear shared preferences.
            sharedPreferences.edit().clear().apply();
            pushEnabled = false;
            shouldEnablePush = true;
        } else {
            endpointArn = sharedPreferences.getString(SHARED_PREFS_KEY_ENDPOINT_ARN, "");
            pushEnabled = sharedPreferences.getBoolean(SHARED_PREFS_PUSH_ENABLED, false);
            shouldEnablePush = pushEnabled;
        }
        gcmTokenHelper.addTokenUpdateObserver(this);
    }

    @Override
    public void onGCMTokenUpdate(final String gcmToken, final boolean didTokenChange) {
        if (didTokenChange || !isRegistered()) {
            try {
                Log.d(LOG_TAG, "GCM Token changed or SNS endpoint not registered.");
                try {
                    createPlatformArn();
                } catch (final AmazonClientException ex) {
                    Log.e(LOG_TAG, "Error creating platform endpoint ARN: " + ex.getMessage(), ex);
                    pushEnabled = false;
                    throw ex;
                }

                try {
                    Log.d(LOG_TAG, "Updating push enabled state to " + shouldEnablePush);
                    setSNSEndpointEnabled(shouldEnablePush);
                } catch (final AmazonClientException ex) {
                    Log.e(LOG_TAG, "Failed to set push enabled state : " + ex, ex);
                    throw ex;
                }

                try {
                    Log.d(LOG_TAG, "Resubscribing to subscribed topics.");
                } catch (final AmazonClientException ex) {
                    Log.e(LOG_TAG, "Failed resubscribing to topics : " + ex, ex);
                    throw ex;
                }
            } catch (final AmazonClientException ex) {
                // Clear the endpoint ARN, regardless of what failed, this will force the app
                // to try again the next time the app is started or registerDevice() is called.
                endpointArn = "";
                Log.e(LOG_TAG, "Push Notifications - FAILED : " + ex, ex);
                return;
            } finally {
                sharedPreferences.edit()
                        .putString(SHARED_PREFS_PREVIOUS_PLATFORM_APPLICATION, platformApplicationArn)
                        .putString(SHARED_PREFS_KEY_ENDPOINT_ARN, endpointArn)
                        // Setting push enabled to whether push should be enabled, so a failure
                        // will not disable push in shared preferences, and the app will retry
                        // when restarted.
                        .putBoolean(SHARED_PREFS_PUSH_ENABLED, shouldEnablePush)
                        .apply();
                informStateListener();
            }
        }
        Log.d(LOG_TAG, "Push Notifications - OK ");
    }

    @Override
    public void onGCMTokenUpdateFailed(final Exception ex) {
        Log.e(LOG_TAG, "Push Notifications - FAILED : GCM registration failed : " + ex, ex);
        pushEnabled = false;
        informStateListener();
    }

    public void registerDevice() {
        // Updates the GCM token, which triggers {@link #onGCMTokenUpdate(String,boolean)} to create the platform
        // arn set push enabled, and re-subscribe to any previously subscribed topics.
        gcmTokenHelper.updateGCMToken();
    }

    private void createPlatformArn() {
        final CreatePlatformEndpointRequest request = new CreatePlatformEndpointRequest();
        request.setPlatformApplicationArn(platformApplicationArn);
        request.setToken(gcmTokenHelper.getGCMToken());
        final CreatePlatformEndpointResult result = sns.createPlatformEndpoint(request);
        endpointArn = result.getEndpointArn();
        Log.d(LOG_TAG, "endpoint arn: " + endpointArn);
    }

    private void setSNSEndpointEnabled(final boolean enabled) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(SNS_ENDPOINT_ATTRIBUTE_ENABLED, String.valueOf(enabled));
        SetEndpointAttributesRequest request = new SetEndpointAttributesRequest();
        request.setEndpointArn(endpointArn);
        request.setAttributes(attr);
        sns.setEndpointAttributes(request);
        Log.d(LOG_TAG, String.format("Set push %s for endpoint arn: %s",
                enabled ? "enabled" : "disabled", endpointArn));
        this.pushEnabled = enabled;
    }

    public void setPushEnabled(final boolean enabled) {
        shouldEnablePush = enabled;
        setSNSEndpointEnabled(enabled);
        informStateListener();
        sharedPreferences.edit()
                .putBoolean(SHARED_PREFS_PUSH_ENABLED, enabled)
                .putString(SHARED_PREFS_PREVIOUS_PLATFORM_APPLICATION, platformApplicationArn)
                .apply();
    }

    public boolean isRegistered() {
        return endpointArn != null && !endpointArn.isEmpty();
    }

    public String getEndpointArn() {
        return endpointArn;
    }


    public static void setPushStateListener(final PushStateListener listener) {
        PushManager.pushStateListener = listener;
    }

    private void informStateListener() {
        if (previousPushState == null || pushEnabled != previousPushState) {
            previousPushState = pushEnabled;
            if (pushStateListener == null) {
                return;
            }
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "PushStateListener: State changed to : " +
                            (pushEnabled ? "PUSH ENABLED" : "PUSH DISABLED"));

                    try {
                        pushStateListener.onPushStateChange(PushManager.this, pushEnabled);
                        Log.d(LOG_TAG, "PushStateListener:onPushStateChange ok");
                    } catch (final Exception e) {
                        Log.e(LOG_TAG, "PushStateListener:onPushStateChange Failed : " + e.getMessage(), e);
                    }
                }
            });
        }
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public AmazonSNS getSns() {
        return sns;
    }
}
