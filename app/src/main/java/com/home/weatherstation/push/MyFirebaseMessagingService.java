package com.home.weatherstation.push;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.home.weatherstation.ScannerService;

/**
 * Created by dominic on 30.04.17.
 */

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = MyFirebaseMessagingService.class.getSimpleName();

    enum ACTION {
        SCAN_AND_UPLOAD_NOW("scan_and_upload"), PUBLISH_LOGS("publish_logs");

        private String action;

        ACTION(String action) {
            this.action = action;
        }

        static ACTION get(String action) {
            for (ACTION a : values()) {
                if (a.action.equals(action)) {
                    return a;
                }
            }
            Log.w(TAG, "Unknown action: " + action);
            return null;
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Data: " + remoteMessage.getData());

        if (remoteMessage.getData().size() > 0) {
            execute(ACTION.get(remoteMessage.getData().get("action")));
        } else {
            Log.w(TAG, "Missing data");
        }
    }

    private void execute(final ACTION action) {
        if (action == null) {
            Log.w(TAG, "Ignoring unknown action");
            return;
        }

        switch (action) {
            case SCAN_AND_UPLOAD_NOW: {
                startService(ScannerService.buildScanAndUploadIntent(this));
                break;
            }
            case PUBLISH_LOGS: {
                Log.w(TAG, "Not yet implemented");
                break;
            }

        }
    }
}
