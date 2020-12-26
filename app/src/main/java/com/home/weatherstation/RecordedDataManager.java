package com.home.weatherstation;

import android.content.Context;
import android.util.Log;

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
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.PasteDataRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecordedDataManager {

    public static final String[] SCOPES = {SheetsScopes.SPREADSHEETS};
    private static final String TAG = RecordedDataManager.class.getSimpleName();

    private Sheets sheetsApi;

    public RecordedDataManager(Sheets sheetsApi) {
        this.sheetsApi = sheetsApi;
    }

    public static Sheets getSheetsApi(Context context) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(new AuthPreferences(context).getUser());

        HttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        return new Sheets.Builder(
                transport, jsonFactory, credential).setApplicationName(context.getString(R.string.app_name)).build();
    }

    public void insertWithRetry(String spreadsheetId, int sheetId, CharSequence timestamp,
                                boolean device8HasValue, String device8Value,
                                boolean device9HasValue, String device9Value,
                                boolean device10HasValue, String device10Value,
                                boolean outsideHasValue, String outsideValue) throws IOException {
        ArrayList<String> exceptionMessages = new ArrayList<>();
        int tries = 0;
        while (tries < 4) {
            tries++;
            try {

                insert(spreadsheetId, sheetId, timestamp,
                        device8HasValue, device8Value,
                        device9HasValue, device9Value,
                        device10HasValue, device10Value,
                        outsideHasValue, outsideValue);
                return;
            } catch (IOException e) {
                exceptionMessages.add(e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    Log.e(TAG, "Could not sleep", e1);
                }
            }
        }
        throw new IOException("Could not insert data to SpreadsheetId " + spreadsheetId +
                "\n Exceptions: " + String.join("\n", exceptionMessages));
    }

    private void insert(String spreadsheetId, int sheetId, CharSequence timestamp,
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
        Log.d(TAG, "Insert new data response: " + response.toPrettyString());
    }

    public float queryAvg(String spreadsheetId) throws IOException {
        String range = "Average!F2:F2";
        ValueRange response = sheetsApi.spreadsheets().values().get(spreadsheetId, range).execute();
        Log.d(TAG, "Read average response: " + response.toPrettyString());

        String avg = (String) response.getValues().get(0).get(0);
        return Float.parseFloat(avg);
    }
}
