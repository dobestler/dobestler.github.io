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
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    public void insert(String spreadsheetId, int sheetId, CharSequence timestamp,
                       boolean device8HasValue, String device8Value,
                       boolean device9HasValue, String device9Value,
                       boolean device10HasValue, String device10Value,
                       boolean outsideHasValue, String outsideValue) throws IOException {

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
        batchUpdateSpreadsheetRequest.setRequests(Collections.singletonList(request1));

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

    public float queryAvg(String spreadsheetId) throws IOException {
        String range = "Average!F2:F2";
        ValueRange response = sheetsApi.spreadsheets().values().get(spreadsheetId, range).execute();
        Log.d(TAG, "Read average response: " + response.toPrettyString());

        String avg = (String) response.getValues().get(0).get(0);
        return Float.parseFloat(avg);
    }
}
