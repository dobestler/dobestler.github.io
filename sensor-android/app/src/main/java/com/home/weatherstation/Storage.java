package com.home.weatherstation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by dominic on 05/07/16.
 * <p>
 * TODO API 30: This might not work for devices running Android 11 (API 30). See here: https://developer.android.com/about/versions/11/privacy/storage
 */
public class Storage {
    private static final String FILENAME = "Storage";

    public static void registerChangeListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        getPrefs(context).registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unregisterChangeListener(Context context, SharedPreferences.OnSharedPreferenceChangeListener listener) {
        getPrefs(context).unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static long readLastScanTime(Context context) {
        return read(context, "last_scan_time");
    }


    public static long readLastSuccessfulScanTime(Context context) {
        return read(context, "last_successful_scan_time");
    }

    public static long readIncompleteScans(Context context) {
        return read(context, "incomplete_scans");
    }

    public static long readLastIncompleteScanAlertTime(Context context) {
        return read(context, "last_incomplete_scan_alert_time");
    }

    public static long readLastUploadTime(Context context) {
        return read(context, "last_upload_time");
    }

    public static void storeNextScheduledScanTime(Context context, long timestamp) {
        write(context, "next_scheduled_scan_time", timestamp);
    }

    public static void storeLastScanTime(Context context, long timestamp) {
        write(context, "last_scan_time", timestamp);
    }


    public static void storeLastSuccessfulScanTime(Context context, long timestamp) {
        write(context, "last_successful_scan_time", timestamp);
    }


    public static void storeIncompleteScans(Context context, long incompleteScans) {
        write(context, "incomplete_scans", incompleteScans);
    }

    public static void storeLastIncompleteScanAlertTime(Context context, long timestamp) {
        write(context, "last_incomplete_scan_alert_time", timestamp);
    }


    public static void storeLastUploadTime(Context context, long timestamp) {
        write(context, "last_upload_time", timestamp);
    }

    public static void storeAlertingConfig(Context context, AlertingConfig alertingConfig) {
        writeFloat(context, "config_humidity_lower_threshold", alertingConfig.getLowerThresholdHumidity());
        writeFloat(context, "config_humidity_upper_threshold", alertingConfig.getUpperThresholdHumidity());
    }

    public static AlertingConfig readAlertingConfig(Context context) {
        AlertingConfig alertingConfig = new AlertingConfig(); // Default values
        if (contains(context, "config_humidity_lower_threshold")) {
            alertingConfig.setLowerThresholdHumidity(readFloat(context, "config_humidity_lower_threshold"));
        }
        if (contains(context, "config_humidity_upper_threshold")) {
            alertingConfig.setUpperThresholdHumidity(readFloat(context, "config_humidity_upper_threshold"));
        }
        return alertingConfig;
    }

    public static void storeAverageHumidity(Context context, float avg) {
        writeFloat(context, "avg_humidity", avg);
    }

    public static float readAverageHumidity(Context context) {
        return readFloat(context, "avg_humidity");
    }


    public static void storeThresholdExceededHumidity(Context context, long timestamp) {
        write(context, "humidity_threshhold_exceeded_time", timestamp);
    }

    public static void removeThresholdExceededHumidity(Context context) {
        remove(context, "humidity_threshhold_exceeded_time");
    }

    public static long readThresholdExceededHumidity(Context context) {
        return read(context, "humidity_threshhold_exceeded_time");
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }

    private static long read(Context context, String key) {
        return getPrefs(context).getLong(key, -1);
    }

    private static float readFloat(Context context, String key) {
        return getPrefs(context).getFloat(key, -1);
    }

    @SuppressLint("CommitPrefEdits")
    private static void remove(Context context, String key) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.remove(key);
        editor.commit();
    }

    @SuppressLint("CommitPrefEdits")
    private static void write(Context context, String key, long value) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putLong(key, value);
        editor.commit();
    }

    @SuppressLint("CommitPrefEdits")
    private static void writeFloat(Context context, String key, float value) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putFloat(key, value);
        editor.commit();
    }

    private static boolean contains(Context context, String key) {
        return getPrefs(context).contains(key);
    }

}
