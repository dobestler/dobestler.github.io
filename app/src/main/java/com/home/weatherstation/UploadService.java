package com.home.weatherstation;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.creativityapps.gmailbackgroundlibrary.BackgroundMail;
import com.google.android.gms.auth.GoogleAuthException;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class UploadService extends IntentService {

    private static final String TAG = UploadService.class.getSimpleName();

    public static final String[] SCOPES = {SheetsScopes.SPREADSHEETS, "https://www.googleapis.com/auth/fusiontables" }; //FIXME remove fusiontable

    private static final String ACTION_UPLOAD = "com.home.weatherstation.action.upload";
    private static final String ACTION_CHECK_THRESHOLDS = "com.home.weatherstation.action.checkthresholds";

    private static final String EXTRA_TIMESTAMP = "com.home.weatherstation.extra.timestamp";
    private static final String EXTRA_SAMPLE_DEVICE8 = "com.home.weatherstation.extra.sampledevice8";
    private static final String EXTRA_SAMPLE_DEVICE9 = "com.home.weatherstation.extra.sampledevice9";
    private static final String EXTRA_SAMPLE_DEVICE10 = "com.home.weatherstation.extra.sampledevice10";
    private static final String EXTRA_ALERTING_CONFIG = "com.home.weatherstation.extra.config";

    private static final String TEMPERATURE_TABLE_ID = "1jQ_Jnnw26pWU05sGBNdXbXlvxB-66_W4fuJgsTG7";
    private static final String TEMPERATURE_SPREADSHEET_ID = "1TDc8o49IiG60Jfmoy-23UU7UfSlUZbYeX4QrnPCQ8d0";
    private static final int TEMPERATURE_DATA_SHEET_ID = 1714261182;
    private static final String HUMIDITY_TABLE_ID = "1sJHjpA2ToIvRbY0eksYhS1hfctq8yg-1H1KPhvaJ";
    private static final String HUMIDITY_SPREADSHEET_ID = "1LVvt-egQB7sXqdyBKtJzBMMZiiBKoAeQL15pMZos7l4";
    private static final int HUMIDITY_DATA_SHEET_ID = 1714261182;
    private static final String BATTERY_TABLE_ID = "13Oox5ACRRPJcaL8CigkkpveWUNV3ALDbEwWpmuvq";
    private static final String BATTERY_SPREADSHEET_ID = "1bqguOW2ovWqVFjXrxp-v2kvzSXy_8zG21yOEgmyyjWk";
    private static final int BATTERY_DATA_SHEET_ID = 1714261182;
    private static final String API_KEY_GOOGLE = "AIzaSyC6bt0RnAVIDwdj3eiSJBmrEPqTmQGDNkM";

    private static final String SMN_STATION_URL = "https://opendata.netcetera.com/smn/smn/REH";

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");

    public UploadService() {
        super("UploadService");
    }

    /**
     * Sends an alert if the average value for the last 7 days is below or above the thresholds.
     */
    public static void checkThresholds(final Context context, final AlertingConfig config) {
        Intent intent = new Intent(context, UploadService.class);
        intent.setAction(ACTION_CHECK_THRESHOLDS);
        intent.putExtra(EXTRA_ALERTING_CONFIG, config);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startUpload(final Context context, final Date timestamp, final Sample sampleDeviceNo8, final Sample sampleDeviceNo9, final Sample sampleDeviceNo10) {
        if (sampleDeviceNo8 == null && sampleDeviceNo9 == null && sampleDeviceNo10 == null) {
            Log.w(TAG, "Not starting upload because all samples are null");
            return;
        }

        Intent intent = new Intent(context, UploadService.class);
        intent.setAction(ACTION_UPLOAD);
        intent.putExtra(EXTRA_TIMESTAMP, timestamp.getTime());
        intent.putExtra(EXTRA_SAMPLE_DEVICE8, getSample("Device8", sampleDeviceNo8));
        intent.putExtra(EXTRA_SAMPLE_DEVICE9, getSample("Device9", sampleDeviceNo9));
        intent.putExtra(EXTRA_SAMPLE_DEVICE10, getSample("Device10", sampleDeviceNo10));
        context.startService(intent);
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
            } catch (IOException | JSONException e) {
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
            int pressure = currentObservation.getInt("qfePressure");

            return new Sample(d, "Outside", tempCurrent, relHumid, Sample.NOT_SET_INT);
        } catch (Exception e) {
            e.printStackTrace();
            return getSample("Outside", null);
        }

    }

    private static Date parseDate(String dateString) {
        DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return utcFormat.parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
            return new Date();
        }
    }

    private void insert(Sheets sheetsApi, Date timestamp, Sample device8, Sample device9, Sample device10, Sample outside) throws IOException, JSONException {
        CharSequence timestampValue = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", timestamp);

//        try {
        //FIXME @deprecated
        //FIXME activate for a while
//            insert(TEMPERATURE_TABLE_ID, timestampValue, device8.hasTempCurrent(), device8.getTemperature(), device9.hasTempCurrent(), device9.getTemperature(), device10.hasTempCurrent(), device10.getTemperature(), outside.hasTempCurrent(), outside.getTemperature());
//            insert(HUMIDITY_TABLE_ID, timestampValue, device8.hasRelativeHumidity(), device8.getRelativeHumidity(), device9.hasRelativeHumidity(), device9.getRelativeHumidity(), device10.hasRelativeHumidity(), device10.getRelativeHumidity(), outside.hasRelativeHumidity(), outside.getRelativeHumidity());
//            insert(BATTERY_TABLE_ID, timestampValue, device8.hasBatteryLevelCurrent(), device8.getBatteryLevel(), device9.hasBatteryLevelCurrent(), device9.getBatteryLevel(), device10.hasBatteryLevelCurrent(), device10.getBatteryLevel(), outside.hasBatteryLevelCurrent(), outside.getBatteryLevel());
//        } catch (GoogleAuthException e) {
//            e.printStackTrace();
//        }

        insert(TEMPERATURE_SPREADSHEET_ID, TEMPERATURE_DATA_SHEET_ID, sheetsApi, timestampValue, device8.hasTempCurrent(), device8.getTemperature(), device9.hasTempCurrent(), device9.getTemperature(), device10.hasTempCurrent(), device10.getTemperature(), outside.hasTempCurrent(), outside.getTemperature());
        insert(HUMIDITY_SPREADSHEET_ID, HUMIDITY_DATA_SHEET_ID, sheetsApi, timestampValue, device8.hasRelativeHumidity(), device8.getRelativeHumidity(), device9.hasRelativeHumidity(), device9.getRelativeHumidity(), device10.hasRelativeHumidity(), device10.getRelativeHumidity(), outside.hasRelativeHumidity(), outside.getRelativeHumidity());
        insert(BATTERY_SPREADSHEET_ID, BATTERY_DATA_SHEET_ID, sheetsApi, timestampValue, device8.hasBatteryLevelCurrent(), device8.getBatteryLevel(), device9.hasBatteryLevelCurrent(), device9.getBatteryLevel(), device10.hasBatteryLevelCurrent(), device10.getBatteryLevel(), outside.hasBatteryLevelCurrent(), outside.getBatteryLevel());
    }

    private void insert(String spreadsheetId, int sheetId, Sheets sheetsApi, CharSequence timestamp, boolean device8HasValue, float device8Value, boolean device9HasValue, float device9Value, boolean device10HasValue, float device10Value, boolean outsideHasValue, float outsideValue) throws IOException, JSONException {
        insert(spreadsheetId, sheetId, sheetsApi, timestamp.toString(), device8HasValue, String.valueOf(device8Value), device9HasValue, String.valueOf(device9Value), device10HasValue, String.valueOf(device10Value), outsideHasValue, String.valueOf(outsideValue));
    }

    private void insert(String spreadsheetId, int sheetId, Sheets sheetsApi, CharSequence timestamp, boolean device8HasValue, int device8Value, boolean device9HasValue, int device9Value, boolean device10HasValue, int device10Value, boolean outsideHasValue, int outsideValue) throws IOException, JSONException {
        insert(spreadsheetId, sheetId, sheetsApi, timestamp.toString(), device8HasValue, String.valueOf(device8Value), device9HasValue, String.valueOf(device9Value), device10HasValue, String.valueOf(device10Value), outsideHasValue, String.valueOf(outsideValue));
    }

    @Deprecated
    private void insert(String table, CharSequence timestamp, boolean device8HasValue, float device8Value, boolean device9HasValue, float device9Value, boolean device10HasValue, float device10Value, boolean outsideHasValue, float outsideValue) throws IOException, GoogleAuthException {
        insert(table, timestamp.toString(), device8HasValue, DECIMAL_FORMAT.format(device8Value), device9HasValue, DECIMAL_FORMAT.format(device9Value), device10HasValue, DECIMAL_FORMAT.format(device10Value), outsideHasValue, DECIMAL_FORMAT.format(outsideValue));
    }

    @Deprecated
    private void insert(String table, CharSequence timestamp, boolean device8HasValue, int device8Value, boolean device9HasValue, int device9Value, boolean device10HasValue, int device10Value, boolean outsideHasValue, int outsideValue) throws IOException, GoogleAuthException {
        insert(table, timestamp.toString(), device8HasValue, String.valueOf(device8Value), device9HasValue, String.valueOf(device9Value), device10HasValue, String.valueOf(device10Value), outsideHasValue, String.valueOf(outsideValue));
    }

    private void insert(String spreadsheetId, int sheetId, Sheets sheetsApi, CharSequence timestamp, boolean device8HasValue, String device8Value, boolean device9HasValue, String device9Value, boolean device10HasValue, String device10Value, boolean outsideHasValue, String outsideValue) throws IOException, JSONException {
        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        Request request = new Request()
                .setInsertDimension(new InsertDimensionRequest()
                        .setRange(new DimensionRange()
                                .setSheetId(sheetId).setDimension("ROWS").setStartIndex(1).setEndIndex(2))
                        .setInheritFromBefore(false));

        batchUpdateSpreadsheetRequest.setRequests(Arrays.asList(request));
        BatchUpdateSpreadsheetResponse insertResponse = sheetsApi.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute();
        Log.d(TAG, "Insert new row response: " + insertResponse.toPrettyString());

        // Write data
        String range = "Data!A2:E2";
        ValueRange content = new ValueRange();
        List<List<Object>> values = new ArrayList<List<Object>>();
        List<Object> vals = Arrays.asList(
                (Object) String.valueOf(timestamp),
                (device8HasValue ? device8Value : ""),
                (device9HasValue ? device9Value : ""),
                (device10HasValue ? device10Value : ""),
                (outsideHasValue ? outsideValue : ""));
        values.add(vals);
        content.setValues(values);
        UpdateValuesResponse response = sheetsApi.spreadsheets().values().update(spreadsheetId, range, content).setValueInputOption("USER_ENTERED").execute();
        Log.d(TAG, "Write data response: " + response.toPrettyString());
        // FIXME rollback inserted row
    }


    @Deprecated
    private void insert(String table, CharSequence timestamp, boolean device8HasValue, String
            device8Value, boolean device9HasValue, String device9Value, boolean device10HasValue, String
                                device10Value, boolean outsideHasValue, String outsideValue) throws IOException, GoogleAuthException {
        // build insert statements
        String rawInsertStatement =
                "INSERT INTO %s (" +
                        "Date" +
                        (device8HasValue ? ",DeviceNo8" : "") +
                        (device9HasValue ? ",DeviceNo9" : "") +
                        (device10HasValue ? ",DeviceNo10" : "") +
                        (outsideHasValue ? ",Outside" : "")
                        + ") VALUES (" +
                        "'%s'" +
                        (device8HasValue ? ", " + device8Value : "") +
                        (device9HasValue ? ", " + device9Value : "") +
                        (device10HasValue ? ", " + device10Value : "") +
                        (outsideHasValue ? ", " + outsideValue : "")
                        + ")";
        String insertStatement = String.format(rawInsertStatement, table, timestamp);

        Log.d(TAG, "Insert statement : " + insertStatement);

        // Encode the query
        String query = URLEncoder.encode(insertStatement);
        URL url = new URL("https://www.googleapis.com/fusiontables/v2/query?sql=" + query + "&key=" + API_KEY_GOOGLE);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        credential.setSelectedAccountName(new AuthPreferences(getApplicationContext()).getUser());
        conn.setRequestProperty("Authorization", "Bearer " + credential.getToken());

        Log.i(TAG, "Response Code: " + conn.getResponseCode());
        Log.i(TAG, "Response Message: " + conn.getResponseMessage());

        // read the response
        BufferedInputStream bis;
        if (200 <= conn.getResponseCode() && conn.getResponseCode() <= 299) {
            bis = new BufferedInputStream(conn.getInputStream());
        } else {
            bis = new BufferedInputStream(conn.getErrorStream());
        }
        String response = org.apache.commons.io.IOUtils.toString(bis, "UTF-8");
        Log.v(TAG, response);
    }

    private void checkThresholds(Sheets sheetsApi, final AlertingConfig alertingConfig) {
        int lastXdays = -4; // should be fetched from Sheets (or maybe sheets should trigger this email alltogether)

        try {
            float averageHum = queryAvg(HUMIDITY_SPREADSHEET_ID, sheetsApi);

            Storage.storeAverageHumidity(this, averageHum);
            if (averageHum < alertingConfig.getLowerThresholdHumidity() || averageHum > alertingConfig.getUpperThresholdHumidity()) {
                Storage.storeThresholdExceededHumidity(this, System.currentTimeMillis());
                sendThresholdExceededAlert("Humidity", averageHum, lastXdays, alertingConfig.getLowerThresholdHumidity(), alertingConfig.getUpperThresholdHumidity());
            } else {
                if (Storage.readThresholdExceededHumidity(this) > -1) {
                    sendThresholdRecoveredAlert("Humidity", averageHum, lastXdays, alertingConfig.getLowerThresholdHumidity(), alertingConfig.getUpperThresholdHumidity());
                }
                Storage.removeThresholdExceededHumidity(this);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendThresholdRecoveredAlert(String tableName, double recoveringValue,
                                             int lastXdays, float lowerThreshold, float upperThreshold) {
        Log.i(TAG, "Sending Threshold Recovered alert email...");
        String subject = String.format("%s Alert: %s threshold recovered", getString(R.string.app_name), tableName);
        sendAlertEmail(recoveringValue, lastXdays, lowerThreshold, upperThreshold, subject);
    }

    private void sendThresholdExceededAlert(String tableName, double exceedingValue,
                                            int lastXdays, float lowerThreshold, float upperThreshold) {
        Log.i(TAG, "Sending Threshold Exceeded alert email...");
        String subject = String.format("%s Alert: %s threshold exceeded", getString(R.string.app_name), tableName);
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
                .withBody(String.format("Measured avg. for the last %d days = %s \n" +
                        "Lower threshold = %s\n" +
                        "Upper threshold = %s", lastXdays, new DecimalFormat("#.##").format(exceedingValue), new DecimalFormat("#.##").format(lowerThreshold), new DecimalFormat("#.##").format(upperThreshold)))
                .withProcessVisibility(false)
                .send();
    }

    public float queryAvg(String spreadsheetId, Sheets sheetsApi) throws IOException, JSONException {
        String range = "Average!F2:F2";
        List<String> results = new ArrayList<String>();
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