package com.home.weatherstation;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.home.weatherstation.smn.SmnData;
import com.home.weatherstation.smn.SmnRecord;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class UploadService extends IntentService {

    private static final int ID_SERVICE = 202;

    private static final String TAG = UploadService.class.getSimpleName();


    private static final String ACTION_UPLOAD = "com.home.weatherstation.action.upload";
    private static final String ACTION_CHECK_THRESHOLDS = "com.home.weatherstation.action.checkthresholds";

    private static final String EXTRA_TIMESTAMP = "com.home.weatherstation.extra.timestamp";
    private static final String EXTRA_SAMPLE_DEVICE8 = "com.home.weatherstation.extra.sampledevice8";
    private static final String EXTRA_SAMPLE_DEVICE9 = "com.home.weatherstation.extra.sampledevice9";
    private static final String EXTRA_SAMPLE_DEVICE10 = "com.home.weatherstation.extra.sampledevice10";
    private static final String EXTRA_ALERTING_CONFIG = "com.home.weatherstation.extra.config";

    private static final String TEMPERATURE_SPREADSHEET_ID = "1TDc8o49IiG60Jfmoy-23UU7UfSlUZbYeX4QrnPCQ8d0";
    private static final String HUMIDITY_SPREADSHEET_ID = "1LVvt-egQB7sXqdyBKtJzBMMZiiBKoAeQL15pMZos7l4";
    private static final String BATTERY_SPREADSHEET_ID = "1bqguOW2ovWqVFjXrxp-v2kvzSXy_8zG21yOEgmyyjWk";
    private static final int TEMPERATURE_DATA_SHEET_ID = 1714261182;
    private static final int HUMIDITY_DATA_SHEET_ID = 1714261182;
    private static final int BATTERY_DATA_SHEET_ID = 1714261182;

    private static final String OPEN_DATA_URL = "https://data.geo.admin.ch/ch.meteoschweiz.messwerte-aktuell/VQHA80.csv";
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");

    RecordedDataManager recordedDataManager;

    public UploadService() {
        super("UploadService");
    }

    /**
     * Starts this service to perform @ACTION_CHECK_THRESHOLDS with the given parameters. If
     * the service is already performing a task this action will be queued.
     * <p>
     * Sends an alert if the average value for the last 7 days is below or above the thresholds.
     *
     * @see IntentService
     */
    public static Intent buildCheckThresholdsIntent(final Context context, final AlertingConfig config) {
        Intent intent = new Intent(context, UploadService.class);
        intent.setAction(ACTION_CHECK_THRESHOLDS);
        intent.putExtra(EXTRA_ALERTING_CONFIG, config);
        return intent;
    }

    /**
     * Starts this service to perform @ACTION_UPLOAD with the given parameters. If
     * the service is already performing a task this action will be queued.
     * <p>
     * Uploads the samples.
     *
     * @see IntentService
     */
    public static Intent buildStartUploadIntent(final Context context, final Date timestamp, final Sample sampleDeviceNo8, final Sample sampleDeviceNo9, final Sample sampleDeviceNo10) {
        if (sampleDeviceNo8 == null && sampleDeviceNo9 == null && sampleDeviceNo10 == null) {
            Log.w(TAG, "Not starting upload because all samples are null");
            return null;
        }

        Intent intent = new Intent(context, UploadService.class);
        intent.setAction(ACTION_UPLOAD);
        intent.putExtra(EXTRA_TIMESTAMP, timestamp.getTime());
        intent.putExtra(EXTRA_SAMPLE_DEVICE8, getSample("Device8", sampleDeviceNo8));
        intent.putExtra(EXTRA_SAMPLE_DEVICE9, getSample("Device9", sampleDeviceNo9));
        intent.putExtra(EXTRA_SAMPLE_DEVICE10, getSample("Device10", sampleDeviceNo10));
        return intent;
    }

    private static Sample getSample(final String name, final Sample sample) {
        if (sample == null) {
            return new Sample(new Date(), name, Sample.NOT_SET_FLOAT, Sample.NOT_SET_INT, Sample.NOT_SET_INT);
        } else {
            return sample;
        }
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        recordedDataManager = new RecordedDataManager(RecordedDataManager.getSheetsApi(getApplicationContext()));

        if (intent != null) {

            Log.v(TAG, "onHandleIntent ACTION = " + intent.getAction());

            ServiceHelper serviceHelper = new ServiceHelper();

            startForeground(ID_SERVICE, serviceHelper.createNotification(this, NotificationManager.IMPORTANCE_NONE, "Uploading samples ...", false));

            final String action = intent.getAction();
            if (ACTION_UPLOAD.equals(action)) {
                final Date timestamp = new Date(intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis()));
                final Sample sampleDevice8 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE8);
                final Sample sampleDevice9 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE9);
                final Sample sampleDevice10 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE10);
                final Sample sampleOutside = fetchCurrentConditionsOutsideOpenDataDirectly(this);
                Log.i(TAG, "" + sampleOutside);
                upload(timestamp, sampleDevice8, sampleDevice9, sampleDevice10, sampleOutside);
            } else if (ACTION_CHECK_THRESHOLDS.equals(action)) {
                checkThresholds((AlertingConfig) intent.getSerializableExtra(EXTRA_ALERTING_CONFIG));
            } else {
                Log.w(TAG, "Unknown action: " + action);
            }
        }
    }

    private void upload(Date timestamp, Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample sampleOutside) {

        CharSequence timestampValue = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", timestamp);

        try {
            recordedDataManager.insertWithRetry(TEMPERATURE_SPREADSHEET_ID, TEMPERATURE_DATA_SHEET_ID, timestampValue.toString(), deviceNo8.hasTempCurrent(), DECIMAL_FORMAT.format(deviceNo8.getTemperature()), deviceNo9.hasTempCurrent(), DECIMAL_FORMAT.format(deviceNo9.getTemperature()), deviceNo10.hasTempCurrent(), DECIMAL_FORMAT.format(deviceNo10.getTemperature()), sampleOutside.hasTempCurrent(), DECIMAL_FORMAT.format(sampleOutside.getTemperature()));
            Storage.storeLastUploadTime(getBaseContext(), System.currentTimeMillis());
        } catch (IOException e) {
            new ExceptionReporter().sendException(this, e);
        }

        try {
            recordedDataManager.insertWithRetry(HUMIDITY_SPREADSHEET_ID, HUMIDITY_DATA_SHEET_ID, timestampValue.toString(), deviceNo8.hasRelativeHumidity(), String.valueOf(deviceNo8.getRelativeHumidity()), deviceNo9.hasRelativeHumidity(), String.valueOf(deviceNo9.getRelativeHumidity()), deviceNo10.hasRelativeHumidity(), String.valueOf(deviceNo10.getRelativeHumidity()), sampleOutside.hasRelativeHumidity(), String.valueOf(sampleOutside.getRelativeHumidity()));
            Storage.storeLastUploadTime(getBaseContext(), System.currentTimeMillis());
        } catch (IOException e) {
            new ExceptionReporter().sendException(this, e);
        }

        try {
            recordedDataManager.insertWithRetry(BATTERY_SPREADSHEET_ID, BATTERY_DATA_SHEET_ID, timestampValue.toString(), deviceNo8.hasBatteryLevelCurrent(), String.valueOf(deviceNo8.getBatteryLevel()), deviceNo9.hasBatteryLevelCurrent(), String.valueOf(deviceNo9.getBatteryLevel()), deviceNo10.hasBatteryLevelCurrent(), String.valueOf(deviceNo10.getBatteryLevel()), sampleOutside.hasBatteryLevelCurrent(), String.valueOf(sampleOutside.getBatteryLevel()));
            Storage.storeLastUploadTime(getBaseContext(), System.currentTimeMillis());
        } catch (IOException e) {
            new ExceptionReporter().sendException(this, e);
        }

    }

    private static Sample fetchCurrentConditionsOutsideOpenDataDirectly(Context context) {
        try {
            URL url = new URL(OPEN_DATA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            Log.v(TAG, "Fetch Outside Conditions - Response Code: " + conn.getResponseCode());
            InputStream in = new BufferedInputStream(conn.getInputStream(), 1024);
            String response = IOUtils.toString(in, StandardCharsets.UTF_8);

            SmnRecord currentObservation = new SmnData(response).getRecordFor("REH");
            Date d = parseDate(context, currentObservation.getDateTime());
            float tempCurrent = Float.parseFloat(currentObservation.getTemperature());
            int relHumid = Math.round(Float.parseFloat(currentObservation.getHumidity()));
            //int pressure = currentObservation.getQfePressure());

            return new Sample(d, "Outside", tempCurrent, relHumid, Sample.NOT_SET_INT);
        } catch (Exception e) {
            new ExceptionReporter().sendException(context, e);
            return getSample("Outside", null);
        }
    }

    private static Date parseDate(Context context, String dateString) {
        DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return utcFormat.parse(dateString);
        } catch (ParseException e) {
            new ExceptionReporter().sendException(context, e);
            return new Date();
        }
    }


    private void checkThresholds(final AlertingConfig alertingConfig) {
        int lastXdays = -4; // should be fetched from Sheets (or maybe sheets should trigger this email altogether)

        try {
            float averageHum = recordedDataManager.queryAvg(HUMIDITY_SPREADSHEET_ID);

            long thresholdExceededHumidityFromStorage = Storage.readThresholdExceededHumidity(this);

            Storage.storeAverageHumidity(this, averageHum);
            final ExceptionReporter exceptionReporter = new ExceptionReporter();
            if (averageHum < alertingConfig.getLowerThresholdHumidity() || averageHum > alertingConfig.getUpperThresholdHumidity()) {
                long now = System.currentTimeMillis();
                if (thresholdExceededHumidityFromStorage == -1) {
                    Storage.storeThresholdExceededHumidity(this, now);
                    exceptionReporter.sendThresholdExceededAlert(this, averageHum, lastXdays, alertingConfig.getLowerThresholdHumidity(), alertingConfig.getUpperThresholdHumidity());
                }
            } else {
                if (Storage.readThresholdExceededHumidity(this) > -1) {
                    exceptionReporter.sendThresholdRecoveredAlert(this, averageHum, lastXdays, alertingConfig.getLowerThresholdHumidity(), alertingConfig.getUpperThresholdHumidity());
                }
                Storage.removeThresholdExceededHumidity(this);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Not enough data to calculate 4d average -> 'n/a' instead of float");
        } catch (IOException e) {
            new ExceptionReporter().sendException(this, e);
        }
    }


}