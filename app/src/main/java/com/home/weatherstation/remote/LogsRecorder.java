package com.home.weatherstation.remote;

import android.content.Context;

import com.hypertrack.hyperlog.DeviceLogModel;
import com.hypertrack.hyperlog.HyperLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LogsRecorder {

    private static final String TAG = LogsRecorder.class.getSimpleName();

    private SheetsProvider sheetsProvider;

    public LogsRecorder(Context context) {
        this.sheetsProvider = SheetsProvider.getInstance(context.getApplicationContext());
    }

    public void record() throws IOException {
        HyperLog.i(TAG, "Recording logs ...");

        final List<String> reversedAndEscapedLogs = new ArrayList<>();
        final List<DeviceLogModel> deviceLogs = HyperLog.getDeviceLogs(true);
        for (DeviceLogModel logEntry : deviceLogs) {
            reversedAndEscapedLogs.add(0, logEntry.getDeviceLog().replace(SheetsProvider.LOGS_DELIMITER, " \\ "));
        }
        sheetsProvider.insertLogsWithRetry(
                SheetsProvider.CONFIG_AND_LOGS_SPREADSHEET_ID,
                SheetsProvider.LOGS_SHEET_ID,
                reversedAndEscapedLogs);
    }
}