package com.home.weatherstation.remote;

import android.content.Context;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.PasteDataRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.home.weatherstation.AuthPreferences;
import com.home.weatherstation.R;
import com.home.weatherstation.util.MyLog;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SheetsProvider {

    public static final String[] SCOPES = {SheetsScopes.SPREADSHEETS};
    public static final String LOGS_DELIMITER = "\n";

    private static final String TAG = SheetsProvider.class.getSimpleName();

    private static SheetsProvider instance = null;

    static final String TEMPERATURE_SPREADSHEET_ID = "1TDc8o49IiG60Jfmoy-23UU7UfSlUZbYeX4QrnPCQ8d0";
    static final String HUMIDITY_SPREADSHEET_ID = "1LVvt-egQB7sXqdyBKtJzBMMZiiBKoAeQL15pMZos7l4";
    static final String BATTERY_SPREADSHEET_ID = "1bqguOW2ovWqVFjXrxp-v2kvzSXy_8zG21yOEgmyyjWk";
    static final String PRECIPITATION_SPREADSHEET_ID = "1hXvcWWTWgMiXYH4mFiLUIuAvt-Y8llF7W8hnBkKd5jU";
    static final int TEMPERATURE_DATA_SHEET_ID = 1714261182;
    static final int HUMIDITY_DATA_SHEET_ID = 1714261182;
    static final int BATTERY_DATA_SHEET_ID = 1714261182;
    static final int PRECIPITATION_DATA_SHEET_ID = 1714261182;

    static final String CONFIG_AND_LOGS_SPREADSHEET_ID = "170j9y4aZxPRHIUCS5WqqtaF60wLBi1iroIF7gXUUgW0";
    static final int LOGS_SHEET_ID = 767230915;

    static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");

    private Sheets sheetsApi;

    private SheetsProvider(Sheets sheetsApi) {
        this.sheetsApi = sheetsApi;
    }

    public static synchronized SheetsProvider getInstance(Context context) {
        if (instance == null) {
            MyLog.v(TAG, "Creating new singleton instance ...");
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    context, Arrays.asList(SCOPES))
                    .setBackOff(new ExponentialBackOff())
                    .setSelectedAccountName(new AuthPreferences(context).getUser());

            HttpTransport transport = new NetHttpTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            Sheets sheetsApi = new Sheets.Builder(transport, jsonFactory, credential).setApplicationName(context.getString(R.string.app_name)).build();
            instance = new SheetsProvider(sheetsApi);
        } else {
            MyLog.v(TAG, "Return existing singleton instance ...");
        }
        return instance;
    }

    public synchronized void insertSamplesWithRetry(String spreadsheetId, int sheetId, CharSequence timestamp,
                                                    boolean device8HasValue, String device8Value,
                                                    boolean device9HasValue, String device9Value,
                                                    boolean device10HasValue, String device10Value,
                                                    boolean outsideHasValue, String outsideValue) throws IOException {
        ArrayList<String> exceptionMessages = new ArrayList<>();
        int tries = 0;
        int maxTries = 3;
        while (tries <= 3) {
            tries++;
            try {
                insertSamples(spreadsheetId, sheetId, timestamp,
                        device8HasValue, device8Value,
                        device9HasValue, device9Value,
                        device10HasValue, device10Value,
                        outsideHasValue, outsideValue);
                return;
            } catch (IOException e) {
                MyLog.w(TAG, tries + "/" + maxTries + ": Failed to insert sample.", e);
                exceptionMessages.add(e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    MyLog.e(TAG, "Could not sleep", e1);
                }
            }
        }
        throw new IOException("Could not insert data to SpreadsheetId " + spreadsheetId +
                "\n Exceptions: " + String.join("\n", exceptionMessages));
    }

    private void insertSamples(String spreadsheetId, int sheetId, CharSequence timestamp,
                               boolean device8HasValue, String device8Value,
                               boolean device9HasValue, String device9Value,
                               boolean device10HasValue, String device10Value,
                               boolean outsideHasValue, String outsideValue) throws IOException {

        String delimiter = ";;";
        List<String> dataList = Arrays.asList(
                String.valueOf(timestamp),
                (device8HasValue ? device8Value : ""),
                (device9HasValue ? device9Value : ""),
                (device10HasValue ? device10Value : ""),
                (outsideHasValue ? outsideValue : ""));

        InsertDimensionRequest insertRow = new InsertDimensionRequest();
        insertRow.setRange(new DimensionRange().setDimension("ROWS").setStartIndex(1).setEndIndex(2).setSheetId(sheetId));
        // PasteDataRequest is a hack to insert new row AND update data with one request
        PasteDataRequest data = new PasteDataRequest().setData(String.join(delimiter, dataList)).setDelimiter(delimiter)
                .setCoordinate(new GridCoordinate().setColumnIndex(0).setRowIndex(1).setSheetId(sheetId));

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest().setRequests(Arrays.asList(
                new Request().setInsertDimension(insertRow),
                new Request().setPasteData(data)
        ));

        BatchUpdateSpreadsheetResponse response = sheetsApi.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute();
        MyLog.d(TAG, "Inserted new samples data. Response: " + response.toPrettyString());
    }

    public synchronized float queryAvg(String spreadsheetId) throws IOException {
        String range = "Average!F2:F2";
        ValueRange response = sheetsApi.spreadsheets().values().get(spreadsheetId, range).execute();
        MyLog.d(TAG, "Read average. Response: " + response.toPrettyString());

        String avg = (String) response.getValues().get(0).get(0);
        return Float.parseFloat(avg);
    }

    public synchronized void insertLogsWithRetry(String spreadsheetId, int sheetId, List<String> logs) throws IOException {
        if (logs.isEmpty()) return;

        ArrayList<String> exceptionMessages = new ArrayList<>();
        int tries = 0;
        int maxTries = 3;
        while (tries <= 3) {
            tries++;
            try {
                insertLogs(spreadsheetId, sheetId, logs);
                return;
            } catch (IOException e) {
                MyLog.w(TAG, tries + "/" + maxTries + ": Failed to insert logs.", e);
                exceptionMessages.add(e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    MyLog.e(TAG, "Could not sleep", e1);
                }
            }
        }
        throw new IOException("Could not insert logs data to SpreadsheetId " + spreadsheetId +
                "\n Exceptions: " + String.join("\n", exceptionMessages));
    }

    private void insertLogs(String spreadsheetId, int sheetId, List<String> logs) throws IOException {

        InsertDimensionRequest insertRowReq = new InsertDimensionRequest();
        insertRowReq.setRange(new DimensionRange()
                .setDimension("ROWS")
                .setStartIndex(0)
                .setEndIndex(logs.size())
                .setSheetId(sheetId));

        // PasteDataRequest is a hack to insert new row AND update data with one request
        PasteDataRequest pasteDataReq = new PasteDataRequest()
                .setData(String.join(LOGS_DELIMITER, logs))
                .setDelimiter(LOGS_DELIMITER)
                .setCoordinate(new GridCoordinate()
                        .setColumnIndex(0)
                        .setRowIndex(0)
                        .setSheetId(sheetId));

        final int maxLogEntries = 20000;
        DeleteDimensionRequest deleteDimensionRequest = new DeleteDimensionRequest();
        deleteDimensionRequest.setRange(new DimensionRange()
                .setDimension("ROWS")
                .setStartIndex(maxLogEntries)
                .setSheetId(sheetId));

        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest().setRequests(Arrays.asList(
                new Request().setInsertDimension(insertRowReq),
                new Request().setPasteData(pasteDataReq),
                new Request().setDeleteDimension(deleteDimensionRequest)
        ));

        BatchUpdateSpreadsheetResponse response = sheetsApi.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute();
        MyLog.d(TAG, "Inserted new logs data. Response: " + response.toPrettyString());
    }
}
