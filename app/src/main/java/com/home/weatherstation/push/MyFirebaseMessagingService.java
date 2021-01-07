package com.home.weatherstation.push;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.home.weatherstation.ExceptionReporter;
import com.home.weatherstation.ScannerService;
import com.home.weatherstation.ServiceHelper;
import com.home.weatherstation.Storage;
import com.home.weatherstation.UploadService;
import com.hypertrack.hyperlog.HyperLog;

import androidx.annotation.NonNull;

/**
 * Created by dominic on 30.04.17.
 */

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = MyFirebaseMessagingService.class.getSimpleName();

    enum ACTION {
        SCAN_AND_UPLOAD_NOW("scan_and_upload"), CHECK_THRESHOLDS("check_thresholds"), PUBLISH_LOGS("publish_logs");

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
            HyperLog.w(TAG, "Unknown action: " + action);
            return null;
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        HyperLog.i(TAG, "onMessageReceived from " + remoteMessage.getFrom() + " : " + remoteMessage.getData());

        if (remoteMessage.getData().size() > 0) {
            execute(ACTION.get(remoteMessage.getData().get("action")));
        } else {
            HyperLog.w(TAG, "Missing data");
        }
    }

    @Override
    public void onNewToken(@NonNull String s) {
        HyperLog.d(TAG, "onNewToken");
        super.onNewToken(s);
    }

    private void execute(final ACTION action) {
        if (action == null) {
            new ExceptionReporter().sendException(this, new IllegalArgumentException("Ignoring unknown action"));
            return;
        }

        switch (action) {
            case SCAN_AND_UPLOAD_NOW: {
                new ServiceHelper().startForegroundService(this, ScannerService.buildScanAndUploadIntent(this));
                break;
            }
            case CHECK_THRESHOLDS: {
                new ServiceHelper().startForegroundService(this, UploadService.buildCheckThresholdsIntent(this, Storage.readAlertingConfig(this)));
                break;
            }
            case PUBLISH_LOGS: {
                new ServiceHelper().startForegroundService(this, UploadService.buildPublishLogsIntent(this));
                break;
            }

        }
    }
}
