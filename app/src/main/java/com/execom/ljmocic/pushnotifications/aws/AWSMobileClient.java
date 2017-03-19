
package com.execom.ljmocic.pushnotifications.aws;

import android.content.Context;
import android.util.Log;

import com.amazonaws.ClientConfiguration;
import com.execom.ljmocic.pushnotifications.Configuration;
import com.execom.ljmocic.pushnotifications.aws.push.GCMTokenHelper;
import com.execom.ljmocic.pushnotifications.aws.push.PushManager;
import com.execom.ljmocic.pushnotifications.aws.user.IdentityManager;
import com.amazonaws.regions.Regions;

public class AWSMobileClient {

    private final static String LOG_TAG = AWSMobileClient.class.getSimpleName();

    private static AWSMobileClient instance;

    private final Context context;

    private ClientConfiguration clientConfiguration;
    private IdentityManager identityManager;
    private GCMTokenHelper gcmTokenHelper;
    private PushManager pushManager;

    public static class Builder {

        private Context applicationContext;
        private String cognitoIdentityPoolID;
        private Regions cognitoRegion;
        private ClientConfiguration clientConfiguration;
        private IdentityManager identityManager;

	/**
	 * Constructor.
	 * @param context Android context.
	 */
        public Builder(final Context context) {
            this.applicationContext = context.getApplicationContext();
        }

	/**
	 * Provides the Amazon Cognito Identity Pool ID.
	 * @param cognitoIdentityPoolID identity pool ID
	 * @return builder
	 */
        public Builder withCognitoIdentityPoolID(final String cognitoIdentityPoolID) {
            this.cognitoIdentityPoolID = cognitoIdentityPoolID;
            return this;
        }
        
	/**
	 * Provides the Amazon Cognito service region.
	 * @param cognitoRegion service region
	 * @return builder
	 */
        public Builder withCognitoRegion(final Regions cognitoRegion) {
            this.cognitoRegion = cognitoRegion;
            return this;
        }

        /**
         * Provides the identity manager.
	 * @param identityManager identity manager
	 * @return builder
	 */
        public Builder withIdentityManager(final IdentityManager identityManager) {
            this.identityManager = identityManager;
            return this;
        }

        /**
         * Provides the client configuration
         * @param clientConfiguration client configuration
         * @return builder
         */
        public Builder withClientConfiguration(final ClientConfiguration clientConfiguration) {
            this.clientConfiguration = clientConfiguration;
            return this;
        }

	/**
	 * Creates the AWS mobile client instance and initializes it.
	 * @return AWS mobile client
	 */
        public AWSMobileClient build() {
            return
                new AWSMobileClient(applicationContext,
                                    cognitoIdentityPoolID,
                                    cognitoRegion,
                                    identityManager,
                                    clientConfiguration);
        }
    }

    private AWSMobileClient(final Context context,
                            final String cognitoIdentityPoolID,
                            final Regions cognitoRegion,
                            final IdentityManager identityManager,
                            final ClientConfiguration clientConfiguration) {

        this.context = context;
        this.identityManager = identityManager;
        this.clientConfiguration = clientConfiguration;


        this.gcmTokenHelper = new GCMTokenHelper(context, Configuration.GOOGLE_CLOUD_MESSAGING_SENDER_ID);
        this.pushManager =
            new PushManager(context,
                            gcmTokenHelper,
                            identityManager.getCredentialsProvider(),
                            Configuration.AMAZON_SNS_PLATFORM_APPLICATION_ARN,
                            clientConfiguration,
                            Configuration.AMAZON_SNS_DEFAULT_TOPIC_ARN,
                            Configuration.AMAZON_SNS_TOPIC_ARNS,
                            Configuration.AMAZON_SNS_REGION);
        gcmTokenHelper.init();
    }

    public static void setDefaultMobileClient(AWSMobileClient client) {
        instance = client;
    }

    public static AWSMobileClient defaultMobileClient() {
        return instance;
    }

    public PushManager getPushManager() {
        return this.pushManager;
    }

    public static void initializeMobileClientIfNecessary(final Context context) {
        if (AWSMobileClient.defaultMobileClient() == null) {
            Log.d(LOG_TAG, "Initializing AWS Mobile Client...");
            final ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setUserAgent(Configuration.AWS_MOBILEHUB_USER_AGENT);
            final IdentityManager identityManager = new IdentityManager(context, clientConfiguration);
            final AWSMobileClient awsClient =
                new AWSMobileClient.Builder(context)
                    .withCognitoRegion(Configuration.AMAZON_COGNITO_REGION)
                    .withCognitoIdentityPoolID(Configuration.AMAZON_COGNITO_IDENTITY_POOL_ID)
                    .withIdentityManager(identityManager)
                    .withClientConfiguration(clientConfiguration)
                    .build();

            AWSMobileClient.setDefaultMobileClient(awsClient);
        }
        Log.d(LOG_TAG, "AWS Mobile Client is OK");
    }

}
