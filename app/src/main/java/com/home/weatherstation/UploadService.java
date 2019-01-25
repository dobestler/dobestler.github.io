package com.home.weatherstation;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.creativityapps.gmailbackgroundlibrary.BackgroundMail;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class UploadService extends IntentService {

    private static final int ID_SERVICE = 202;

    private static final String TAG = UploadService.class.getSimpleName();

    public static final String[] SCOPES = {SheetsScopes.SPREADSHEETS};

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

    private static final String SMN_STATION_URL = "https://opendata.netcetera.com/smn/smn/REH";

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");

    public UploadService() {
        super("UploadService");
    }

    /**
     * Sends an alert if the average value for the last 7 days is below or above the thresholds.
     */
    public static Intent checkThresholds(final Context context, final AlertingConfig config) {
        Intent intent = new Intent(context, UploadService.class);
        intent.setAction(ACTION_CHECK_THRESHOLDS);
        intent.putExtra(EXTRA_ALERTING_CONFIG, config);
        return intent;
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
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
        if (intent != null) {

            ServiceHelper serviceHelper = new ServiceHelper();

            startForeground(ID_SERVICE, serviceHelper.createNotification(this, NotificationManager.IMPORTANCE_NONE, "Uploading samples ...", false));

            final String action = intent.getAction();
            if (ACTION_UPLOAD.equals(action)) {
                final Date timestamp = new Date(intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis()));
                final Sample sampleDevice8 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE8);
                final Sample sampleDevice9 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE9);
                final Sample sampleDevice10 = intent.getParcelableExtra(EXTRA_SAMPLE_DEVICE10);
                final Sample sampleOutside = fetchCurrentConditionsOutsideSMN();
                Log.i(TAG, "" + sampleOutside);
                upload(getSheetsApi(), timestamp, sampleDevice8, sampleDevice9, sampleDevice10, sampleOutside);
            } else if (ACTION_CHECK_THRESHOLDS.equals(action)) {
                checkThresholds(getSheetsApi(), (AlertingConfig) intent.getSerializableExtra(EXTRA_ALERTING_CONFIG));
            } else {
                Log.w(TAG, "Unknown action: " + action);
            }
        }
    }

    private void upload(Sheets sheetsApi, Date timestamp, Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample sampleOutside) {
        int tries = 0;
        while (tries < 4) {
            tries++;
            try {
                insert(sheetsApi, timestamp, deviceNo8, deviceNo9, deviceNo10, sampleOutside);
                Storage.storeLastUploadTime(getBaseContext(), System.currentTimeMillis());
                return;
            } catch (IOException e) {
                Log.e(TAG, "Could not insert data!", e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private static Sample fetchCurrentConditionsOutsideSMN() {
        try {
            URL url = new URL(SMN_STATION_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // read the response
            Log.i(TAG, "Response Code: " + conn.getResponseCode());
            InputStream in = new BufferedInputStream(conn.getInputStream());
            String response = org.apache.commons.io.IOUtils.toString(in, "UTF-8");
            Log.v(TAG, response);

            JSONObject currentObservation = new JSONObject(response);
            Date d = parseDate(currentObservation.getString("dateTime"));
            float tempCurrent = Float.valueOf(currentObservation.getString("temperature"));
            int relHumid = Integer.valueOf(currentObservation.getString("humidity"));
            //int pressure = currentObservation.getInt("qfePressure");

            return new Sample(d, "Outside", tempCurrent, relHumid, Sample.NOT_SET_INT);
        } catch (Exception e) {
            e.printStackTrace();
            return getSample("Outside", null);
        }

    }

    private static Date parseDate(String dateString) {
        DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return utcFormat.parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
            return new Date();
        }
    }

    private void insert(Sheets sheetsApi, Date timestamp, Sample device8, Sample device9, Sample device10, Sample outside) throws IOException {
        CharSequence timestampValue = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", timestamp);
        insert(TEMPERATURE_SPREADSHEET_ID, TEMPERATURE_DATA_SHEET_ID, sheetsApi, timestampValue.toString(), device8.hasTempCurrent(), DECIMAL_FORMAT.format(device8.getTemperature()), device9.hasTempCurrent(), DECIMAL_FORMAT.format(device9.getTemperature()), device10.hasTempCurrent(), DECIMAL_FORMAT.format(device10.getTemperature()), outside.hasTempCurrent(), DECIMAL_FORMAT.format(outside.getTemperature()));
        insert(HUMIDITY_SPREADSHEET_ID, HUMIDITY_DATA_SHEET_ID, sheetsApi, timestampValue.toString(), device8.hasRelativeHumidity(), String.valueOf(device8.getRelativeHumidity()), device9.hasRelativeHumidity(), String.valueOf(device9.getRelativeHumidity()), device10.hasRelativeHumidity(), String.valueOf(device10.getRelativeHumidity()), outside.hasRelativeHumidity(), String.valueOf(outside.getRelativeHumidity()));
        insert(BATTERY_SPREADSHEET_ID, BATTERY_DATA_SHEET_ID, sheetsApi, timestampValue.toString(), device8.hasBatteryLevelCurrent(), String.valueOf(device8.getBatteryLevel()), device9.hasBatteryLevelCurrent(), String.valueOf(device9.getBatteryLevel()), device10.hasBatteryLevelCurrent(), String.valueOf(device10.getBatteryLevel()), outside.hasBatteryLevelCurrent(), String.valueOf(outside.getBatteryLevel()));
    }

    private void insert(String spreadsheetId, int sheetId, Sheets sheetsApi, CharSequence timestamp, boolean device8HasValue, String device8Value, boolean device9HasValue, String device9Value, boolean device10HasValue, String device10Value, boolean outsideHasValue, String outsideValue) throws IOException {
        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();

        Request request1 = new Request()
                .setInsertDimension(new InsertDimensionRequest()
                        .setRange(new DimensionRange()
                                .setSheetId(sheetId).setDimension("ROWS").setStartIndex(1).setEndIndex(2))
                        .setInheritFromBefore(false));


//        List<CellData> values = new ArrayList<>();
//        values.add(new CellData().setUserEnteredFormat(new CellFormat().setNumberFormat(NumberFormat)))
//        values.add(new CellData().setEffectiveValue(new ExtendedValue().setStringValue(String.valueOf(timestamp))));
//        values.add(new CellData().setEffectiveValue(new ExtendedValue().setStringValue(String.valueOf(device8HasValue ? device8Value : ""))));
//
//        Request request2 = new Request()
//                .setUpdateCells(new UpdateCellsRequest()
//                        .setStart(new GridCoordinate().setSheetId(sheetId).setRowIndex(1).setColumnIndex(0))
//                        .setFields("*")
//                        .setRows(Arrays.asList(new RowData().setValues(values)))
//                );
//
//        Log.d("REQ", request2.toPrettyString());
//        batchUpdateSpreadsheetRequest.setRequests(Arrays.asList(request1, request2));
        batchUpdateSpreadsheetRequest.setRequests(Arrays.asList(request1));

        BatchUpdateSpreadsheetResponse response = sheetsApi.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute();
        Log.d(TAG, "Insert new row response: " + response.toPrettyString());

        // Write data TODO test above and remove below
        String range = "Data!A2:E2";
        ValueRange content = new ValueRange();
        List<List<Object>> values = new ArrayList<>();
        List<Object> vals = Arrays.asList(
                (Object) String.valueOf(timestamp),
                (device8HasValue ? device8Value : ""),
                (device9HasValue ? device9Value : ""),
                (device10HasValue ? device10Value : ""),
                (outsideHasValue ? outsideValue : ""));
        values.add(vals);
        content.setValues(values);
        UpdateValuesResponse response2 = sheetsApi.spreadsheets().values().update(spreadsheetId, range, content).setValueInputOption("USER_ENTERED").execute();
        Log.d(TAG, "Write data response: " + response2.toPrettyString());
    }

    private void checkThresholds(Sheets sheetsApi, final AlertingConfig alertingConfig) {
        int lastXdays = -4; // should be fetched from Sheets (or maybe sheets should trigger this email alltogether)

        try {
            float averageHum = queryAvg(HUMIDITY_SPREADSHEET_ID, sheetsApi);

            long thresholdExceededHumidityFromStorage = Storage.readThresholdExceededHumidity(this);

            Storage.storeAverageHumidity(this, averageHum);
            if (averageHum < alertingConfig.getLowerThresholdHumidity() || averageHum > alertingConfig.getUpperThresholdHumidity()) {
                // send alert only every 8 hours
                long now = System.currentTimeMillis();
                if (thresholdExceededHumidityFromStorage == -1 || (now - thresholdExceededHumidityFromStorage > TimeUnit.HOURS.toMillis(8))) {
                    Storage.storeThresholdExceededHumidity(this, now);
                    sendThresholdExceededAlert(averageHum, lastXdays, alertingConfig.getLowerThresholdHumidity(), alertingConfig.getUpperThresholdHumidity());
                }
            } else {
                if (Storage.readThresholdExceededHumidity(this) > -1) {
                    sendThresholdRecoveredAlert(averageHum, lastXdays, alertingConfig.getLowerThresholdHumidity(), alertingConfig.getUpperThresholdHumidity());
                }
                Storage.removeThresholdExceededHumidity(this);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendThresholdRecoveredAlert(double recoveringValue,
                                             int lastXdays, float lowerThreshold, float upperThreshold) {
        Log.i(TAG, "Sending Threshold Recovered alert email...");
        String subject = String.format("%s Alert: %s threshold recovered", getString(R.string.app_name), "Humidity");
        sendAlertEmail(recoveringValue, lastXdays, lowerThreshold, upperThreshold, subject);
    }

    private void sendThresholdExceededAlert(double exceedingValue,
                                            int lastXdays, float lowerThreshold, float upperThreshold) {
        Log.i(TAG, "Sending Threshold Exceeded alert email...");
        String subject = String.format("%s Alert: %s threshold exceeded", getString(R.string.app_name), "Humidity");
        sendAlertEmail(exceedingValue, lastXdays, lowerThreshold, upperThreshold, subject);
    }

    private void sendAlertEmail(double exceedingValue, int lastXdays, float lowerThreshold,
                                float upperThreshold, String subject) {
        BackgroundMail.newBuilder(this)
                .withUsername(BuildConfig.ALERT_EMAIL_FROM)
                .withPassword(BuildConfig.ALERT_EMAIL_PASSWORD)
                .withMailto(BuildConfig.ALERT_EMAIL_TO)
                .withType(BackgroundMail.TYPE_PLAIN)
                .withSubject(subject)
                .withBody(String.format(Locale.getDefault(), "Measured avg. for the last %d days = %s \n" +
                        "Lower threshold = %s\n" +
                        "Upper threshold = %s", lastXdays, new DecimalFormat("#.##").format(exceedingValue), new DecimalFormat("#.##").format(lowerThreshold), new DecimalFormat("#.##").format(upperThreshold)))
                .withProcessVisibility(false)
                .send();
    }

    public float queryAvg(String spreadsheetId, Sheets sheetsApi) throws IOException {
        String range = "Average!F2:F2";
        ValueRange response = sheetsApi.spreadsheets().values().get(spreadsheetId, range).execute();
        Log.d(TAG, "Read average response: " + response.toPrettyString());
        return Float.parseFloat((String) response.getValues().get(0).get(0));
    }

    private Sheets getSheetsApi() {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        credential.setSelectedAccountName(new AuthPreferences(getApplicationContext()).getUser());

        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        return new Sheets.Builder(
                transport, jsonFactory, credential).setApplicationName(getString(R.string.app_name)).build();
    }

}