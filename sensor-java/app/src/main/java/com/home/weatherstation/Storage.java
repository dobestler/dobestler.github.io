package com.home.weatherstation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class Storage {

    private static final String TAG = Storage.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    private static final String FILENAME = "Storage.properties";

    private static Storage instance;

    private final Properties props;

    private Storage() {
        super();
        props = new Properties();
    }

    public static synchronized Storage getInstance() {
        if (instance == null) {
            logger.trace(TAG, "Creating new singleton instance ...");
            instance = new Storage();
            instance.load();
        }
        return instance;
    }

    public static long readLastScanTime() {
        return getInstance().getLong("last_scan_time");
    }

    public static long readLastSuccessfulScanTime() {
        return getInstance().getLong("last_successful_scan_time");
    }

    public static long readIncompleteScans(String deviceName) {
        return getInstance().getLong("incomplete_scans_" + deviceName);
    }

    public static long readLastIncompleteScanAlertTime() {
        return getInstance().getLong("last_incomplete_scan_alert_time");
    }

    public static long readLastUploadTime() {
        return getInstance().getLong("last_upload_time");
    }

    public static void storeLastScanTime(long timestamp) {
        getInstance().write("last_scan_time", timestamp);
    }

    public static void storeLastSuccessfulScanTime(long timestamp) {
        getInstance().write("last_successful_scan_time", timestamp);
    }


    public static void storeIncompleteScans(long incompleteScans, String deviceName) {
        getInstance().write("incomplete_scans_" + deviceName, incompleteScans);
    }

    public static void storeLastIncompleteScanAlertTime(long timestamp) {
        getInstance().write("last_incomplete_scan_alert_time", timestamp);
    }


    public static void storeLastUploadTime(long timestamp) {
        getInstance().write("last_upload_time", timestamp);
    }

    public static void storeAlertingConfig(AlertingConfig alertingConfig) {
        getInstance().write("config_humidity_lower_threshold", alertingConfig.getLowerThresholdHumidity());
        getInstance().write("config_humidity_upper_threshold", alertingConfig.getUpperThresholdHumidity());
    }

    public static AlertingConfig readAlertingConfig() {
        AlertingConfig alertingConfig = new AlertingConfig(); // Default values
        if (contains("config_humidity_lower_threshold")) {
            alertingConfig.setLowerThresholdHumidity(getInstance().getFloat("config_humidity_lower_threshold"));
        }
        if (contains("config_humidity_upper_threshold")) {
            alertingConfig.setUpperThresholdHumidity(getInstance().getFloat("config_humidity_upper_threshold"));
        }
        return alertingConfig;
    }

    public static void storeAverageHumidity(float avg) {
        getInstance().write("avg_humidity", avg);
    }

    public static float readAverageHumidity() {
        return getInstance().getFloat("avg_humidity");
    }

    public static void storeThresholdExceededHumidity(long timestamp) {
        getInstance().write("humidity_threshhold_exceeded_time", timestamp);
    }

    public static void removeThresholdExceededHumidity() {
        remove("humidity_threshhold_exceeded_time");
    }

    public static long readThresholdExceededHumidity() {
        return getInstance().getLong("humidity_threshhold_exceeded_time");
    }

    public static void removeIncompleteScans(String... deviceNames) {
        for (String deviceName: deviceNames) {
            remove("incomplete_scans_" + deviceName);
        }
    }

    private static void remove(String key) {
        getInstance().props.remove(key);
        getInstance().store();
    }

    private static boolean contains(String key) {
        return getInstance().props.contains(key);
    }

    private void write(String key, long value) {
        write(key, String.valueOf(value));
    }

    private void write(String key, float value) {
        write(key, String.valueOf(value));
    }

    private void write(String key, String value) {
        props.setProperty(key, value);
        store();
    }


    private long getLong(String key) {
        String value = props.getProperty(key, "-1");
        return Long.parseLong(value);
    }

    private float getFloat(String key) {
        String value = props.getProperty(key, "-1");
        return Float.parseFloat(value);
    }


    private synchronized void load() {
        FileInputStream in = null;
        try {
            in = new FileInputStream(FILENAME);
            props.load(in);
        } catch (IOException e) {
            new ExceptionReporter().sendException(e, TAG, "Failed to load Storage");
        } finally {
            try {
                if (in != null) in.close();
            } catch (IOException e) {
                new ExceptionReporter().sendException(e, TAG, "Failed to close FileInputStream");
            }
        }
    }

    private synchronized void store() {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(FILENAME);
            props.store(out, null);
        } catch (IOException e) {
            new ExceptionReporter().sendException(e, TAG, "Failed to store Storage");
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException e) {
                new ExceptionReporter().sendException(e, TAG, "Failed to close FileOutputStream");
            }
        }
    }

}
