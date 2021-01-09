package com.home.weatherstation;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import com.home.weatherstation.remote.LogsRecorder;
import com.home.weatherstation.remote.SamplesRecorder;
import com.home.weatherstation.smn.SmnData;
import com.home.weatherstation.smn.SmnRecord;
import com.home.weatherstation.util.MyLog;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
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
    private static final String ACTION_PUBLISH_LOGS = "com.home.weatherstation.action.publishlogs";

    private static final String EXTRA_TIMESTAMP = "com.home.weatherstation.extra.timestamp";
    private static final String EXTRA_SAMPLE_DEVICE8 = "com.home.weatherstation.extra.sampledevice8";
    private static final String EXTRA_SAMPLE_DEVICE9 = "com.home.weatherstation.extra.sampledevice9";
    private static final String EXTRA_SAMPLE_DEVICE10 = "com.home.weatherstation.extra.sampledevice10";
    private static final String EXTRA_ALERTING_CONFIG = "com.home.weatherstation.extra.config";

    private static final String OPEN_DATA_URL = "https://data.geo.admin.ch/ch.meteoschweiz.messwerte-aktuell/VQHA80.csv";

    public UploadService() {
        super("UploadService");
    }

    /**
     * Starts this service to perform @ACTION_PUBLISH_LOGS with the given parameters. If
     * the service is already performing a task this action will be queued.
     * <p>
     * Publishes the available logs.
     *
     * @see IntentService
     */
    public static Intent buildPublishLogsIntent(final Context context) {
        Intent intent = new Intent(context, UploadService.class);
        intent.setAction(ACTION_PUBLISH_LOGS);
        return intent;
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
            MyLog.w(TAG, "Not starting upload because all samples are null");
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
        if (intent != null) {
            MyLog.d(TAG, "onHandleIntent ACTION = " + intent.getAction());

            startForeground(ID_SERVICE, new ServiceHelper().createNotification(this, NotificationManager.IMPORTANCE_NONE, "Uploading samples ...", false));

            final String action = intent.getAction();

            if (ACTION_UPLOAD.equals(action)) {
                final Date timestamp = new Date(intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis()));
                final Sample sampleDevice8 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE8);
                final Sample sampleDevice9 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE9);
                final Sample sampleDevice10 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE10);
                final Sample sampleOutside = fetchCurrentConditionsOutsideOpenDataDirectly(this);
                upload(timestamp, sampleDevice8, sampleDevice9, sampleDevice10, sampleOutside);

            } else if (ACTION_CHECK_THRESHOLDS.equals(action)) {
                checkThresholds((AlertingConfig) intent.getSerializableExtra(EXTRA_ALERTING_CONFIG));

            } else if (ACTION_PUBLISH_LOGS.equals(action)) {
                uploadLogs();

            } else {
                MyLog.w(TAG, "Unknown action: " + action);
            }
        } else {
            MyLog.w(TAG, "onHandleIntent Intent is null");
        }
    }

    private void upload(Date timestamp, Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample sampleOutside) {
        try {
            new SamplesRecorder(this).record(timestamp, deviceNo8, deviceNo9, deviceNo10, sampleOutside);
            Storage.storeLastUploadTime(getBaseContext(), System.currentTimeMillis());
        } catch (Exception e) {
            new ExceptionReporter().sendException(this, e);
        }
    }

    private void uploadLogs() {
        try {
            new LogsRecorder(this).record();
        } catch (IOException e) {
            new ExceptionReporter().sendException(this, e);
        }
    }

    private static Sample fetchCurrentConditionsOutsideOpenDataDirectly(Context context) {
        MyLog.d(TAG, "Fetching Outside Conditions ...");

        try {
            URL url = new URL(OPEN_DATA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            MyLog.d(TAG, "Fetch Outside Conditions - Response Code: " + conn.getResponseCode());
            InputStream in = new BufferedInputStream(conn.getInputStream(), 1024);
            String response = IOUtils.toString(in, StandardCharsets.UTF_8);

            SmnRecord currentObservation = new SmnData(response).getRecordFor("REH");
            Date d = parseDate(context, currentObservation.getDateTime());
            float tempCurrent = Float.parseFloat(currentObservation.getTemperature());
            int relHumid = Math.round(Float.parseFloat(currentObservation.getHumidity()));
            //int pressure = currentObservation.getQfePressure());

            return new Sample(d, "Outside", tempCurrent, relHumid, Sample.NOT_SET_INT);
        } catch (Exception e) {
            new ExceptionReporter().sendException(context, e, TAG, "Failed to fetch Outside Conditions.");
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
            float averageHum = new SamplesRecorder(this).queryAvgHumidity();

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
            MyLog.w(TAG, "Not enough data to calculate 4d average -> 'n/a' instead of float");
        } catch (IOException e) {
            new ExceptionReporter().sendException(this, e);
        }
    }

}