package com.execom.ljmocic.pushnotifications.aws.user;

import android.content.Context;
import android.util.Log;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.execom.ljmocic.pushnotifications.Configuration;
import com.execom.ljmocic.pushnotifications.utils.ThreadUtils;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IdentityManager {

    public interface IdentityHandler {
        public void handleIdentityID(final String identityId);

        public void handleError(final Exception exception);
    }

    private static final String LOG_TAG = IdentityManager.class.getSimpleName();

    private CognitoCachingCredentialsProvider credentialsProvider;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public IdentityManager(final Context appContext, final ClientConfiguration clientConfiguration) {
        Log.d(LOG_TAG, "IdentityManager init");
        initializeCognito(appContext, clientConfiguration);
        // Ensures that userID is cached.
        getUserID(null);
    }

    private void initializeCognito(final Context context, final ClientConfiguration clientConfiguration) {
        credentialsProvider =
            new CognitoCachingCredentialsProvider(context,
                Configuration.AMAZON_COGNITO_IDENTITY_POOL_ID,
                Configuration.AMAZON_COGNITO_REGION,
                clientConfiguration
            );

    }

    public boolean areCredentialsExpired() {

        final Date credentialsExpirationDate =
            credentialsProvider.getSessionCredentitalsExpiration();

        if (credentialsExpirationDate == null) {
            Log.d(LOG_TAG, "Credentials are EXPIRED.");
            return true;
        }

        long currentTime = System.currentTimeMillis() -
            (long)(SDKGlobalConfiguration.getGlobalTimeOffset() * 1000);

        final boolean credsAreExpired =
            (credentialsExpirationDate.getTime() - currentTime) < 0;

        Log.d(LOG_TAG, "Credentials are " + (credsAreExpired ? "EXPIRED." : "OK"));

        return credsAreExpired;
    }

    public CognitoCachingCredentialsProvider getCredentialsProvider() {
        return this.credentialsProvider;
    }

    public String getCachedUserID() {
        return getCredentialsProvider().getCachedIdentityId();
    }

    public void getUserID(final IdentityHandler handler) {

        new Thread(new Runnable() {
            Exception exception = null;

            @Override
            public void run() {
                String identityId = null;

                try {
                    // Retrieve the user identity on the background thread.
                    identityId = getCredentialsProvider().getIdentityId();
                } catch (final Exception exception) {
                    this.exception = exception;
                    Log.e(LOG_TAG, exception.getMessage(), exception);
                } finally {

                    if (handler == null) {
                        return;
                    }

                    final String result = identityId;

                    ThreadUtils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            if (exception != null) {
                                handler.handleError(exception);
                                return;
                            }

                            handler.handleIdentityID(result);
                        }
                    });
                }
            }
        }).start();
    }
}
