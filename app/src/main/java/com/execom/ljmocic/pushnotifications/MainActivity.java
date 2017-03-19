package com.execom.ljmocic.pushnotifications;


import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.execom.ljmocic.pushnotifications.aws.AWSMobileClient;
import com.execom.ljmocic.pushnotifications.aws.push.PushManager;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String LOG_TAG = MainActivity.class.getName();

    // AWS
    PushManager pushManager;

    // UI elements
    EditText notificationSubject;
    EditText notificationMessage;
    Button sendNotification;
    CheckBox firstReceiver;
    CheckBox secondReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //init pushManager
        pushManager = AWSMobileClient.defaultMobileClient().getPushManager();

        // Init UI elements
        notificationSubject = (EditText) findViewById(R.id.notification_subject);
        notificationMessage = (EditText) findViewById(R.id.notification_message);
        sendNotification = (Button) findViewById(R.id.send_notification);
        firstReceiver = (CheckBox) findViewById(R.id.checkbox_first_receiver);
        secondReceiver = (CheckBox) findViewById(R.id.checkbox_second_receiver);

        //setListeners
        sendNotification.setOnClickListener(this);

    }

    @Override
    public void onClick(View view) {

        switch (view.getId()){

            case R.id.send_notification:
                if(notificationSubject.getText().toString().isEmpty() || notificationMessage.getText().toString().isEmpty()){
                    notifyToast(getString(R.string.please_wait));
                }
                else{
                    if(firstReceiver.isChecked()){
                        sendNotification(notificationSubject.getText().toString(), notificationMessage.getText().toString(), Configuration.endpointARN_1);
                    }
                    if(secondReceiver.isChecked()){
                        sendNotification(notificationSubject.getText().toString(), notificationMessage.getText().toString(), Configuration.endpointARN_2);
                    }
                }
        }
    }

    public void sendNotification(final String subject, final String message, final String ARN){

        final ProgressDialog dialog = showWaitingDialog(R.string.please_wait);

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(final Void... params) {

                PublishRequest publishRequest = new PublishRequest()
                        .withTargetArn(ARN)
                        .withSubject(subject)
                        .withMessage(message);

                try {

                    pushManager.getSns().publish(publishRequest);

                    return null;
                } catch (final AmazonClientException ace) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final String errorMessage) {
                dialog.dismiss();
            }
        }.execute();

    }

    private void notifyToast(String message){

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

    }

    private ProgressDialog showWaitingDialog(final int resId, final Object... args) {

        return ProgressDialog.show(this, getString(R.string.please_wait), getString(resId, (Object[]) args));

    }

}
