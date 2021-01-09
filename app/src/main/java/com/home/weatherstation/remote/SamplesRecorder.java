package com.home.weatherstation.remote;

import android.content.Context;

import com.home.weatherstation.Sample;
import com.home.weatherstation.util.MyLog;
import com.hypertrack.hyperlog.HyperLog;

import java.io.IOException;
import java.util.Date;

public class SamplesRecorder {

    private static final String TAG = SamplesRecorder.class.getSimpleName();

    private SheetsProvider sheetsProvider;

    public SamplesRecorder(Context context) {
        this.sheetsProvider = SheetsProvider.getInstance(context.getApplicationContext());
    }

    public void record(Date timestamp, Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample outside) throws IOException {
        CharSequence timestampValue = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", timestamp);

        MyLog.i(TAG, "Recording TEMPERATURE samples ...");
        sheetsProvider.insertSamplesWithRetry(
                SheetsProvider.TEMPERATURE_SPREADSHEET_ID,
                SheetsProvider.TEMPERATURE_DATA_SHEET_ID,
                timestampValue.toString(),
                deviceNo8.hasTempCurrent(), SheetsProvider.DECIMAL_FORMAT.format(deviceNo8.getTemperature()),
                deviceNo9.hasTempCurrent(), SheetsProvider.DECIMAL_FORMAT.format(deviceNo9.getTemperature()),
                deviceNo10.hasTempCurrent(), SheetsProvider.DECIMAL_FORMAT.format(deviceNo10.getTemperature()),
                outside.hasTempCurrent(), SheetsProvider.DECIMAL_FORMAT.format(outside.getTemperature()));

        MyLog.i(TAG, "Recording HUMIDITY samples ...");
        sheetsProvider.insertSamplesWithRetry(
                SheetsProvider.HUMIDITY_SPREADSHEET_ID,
                SheetsProvider.HUMIDITY_DATA_SHEET_ID,
                timestampValue.toString(),
                deviceNo8.hasRelativeHumidity(), String.valueOf(deviceNo8.getRelativeHumidity()),
                deviceNo9.hasRelativeHumidity(), String.valueOf(deviceNo9.getRelativeHumidity()),
                deviceNo10.hasRelativeHumidity(), String.valueOf(deviceNo10.getRelativeHumidity()),
                outside.hasRelativeHumidity(), String.valueOf(outside.getRelativeHumidity()));

        MyLog.i(TAG, "Recording BATTERY samples ...");
        sheetsProvider.insertSamplesWithRetry(
                SheetsProvider.BATTERY_SPREADSHEET_ID,
                SheetsProvider.BATTERY_DATA_SHEET_ID,
                timestampValue.toString(),
                deviceNo8.hasBatteryLevelCurrent(), String.valueOf(deviceNo8.getBatteryLevel()),
                deviceNo9.hasBatteryLevelCurrent(), String.valueOf(deviceNo9.getBatteryLevel()),
                deviceNo10.hasBatteryLevelCurrent(), String.valueOf(deviceNo10.getBatteryLevel()),
                outside.hasBatteryLevelCurrent(), String.valueOf(outside.getBatteryLevel()));
    }

    public float queryAvgHumidity() throws IOException {
        MyLog.i(TAG, "Reading average humidity data ...");
        return sheetsProvider.queryAvg(SheetsProvider.HUMIDITY_SPREADSHEET_ID);
    }
}