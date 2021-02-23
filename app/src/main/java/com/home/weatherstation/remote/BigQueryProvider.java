package com.home.weatherstation.remote;

import android.content.Context;
import android.content.res.AssetManager;

import com.google.cloud.AuthCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.QueryRequest;
import com.google.cloud.bigquery.QueryResponse;
import com.home.weatherstation.util.MyLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class BigQueryProvider {

    private static final String TAG = BigQueryProvider.class.getSimpleName();
    private static final String DATASET = "Data";

    private static BigQueryProvider instance = null;

    static final String TABLE_TEMPERATURE = "Temperature";
    static final String TABLE_HUMIDITY = "Humidity";
    static final String TABLE_BATTERY = "Battery";
    static final String TABLE_PRECIPITATION = "Precipitation";

    private BigQuery bigQueryApi;

    private BigQueryProvider(BigQuery bigQueryApi) {
        this.bigQueryApi = bigQueryApi;
    }

    public static synchronized BigQueryProvider getInstance(Context context) {
        if (instance == null) {
            MyLog.v(TAG, "Creating new singleton instance ...");

            try {
                AssetManager am = context.getAssets();
                InputStream inputStream = am.open("WeatherStation-0f7089f5fac7.json");
                AuthCredentials authCredentials = AuthCredentials.createForJson(inputStream);
                BigQuery bigQueryApi =
                        BigQueryOptions.builder()
                                .authCredentials(authCredentials)
                                .projectId("weatherstation-1347")
                                .build().service();

                instance = new BigQueryProvider(bigQueryApi);
            } catch (IOException e) {
                MyLog.e(TAG, "Could not create BigQuery API.", e);
            }

        } else {
            MyLog.v(TAG, "Return existing singleton instance ...");
        }
        return instance;
    }

    public synchronized void insertSamplesWithRetry(String table, CharSequence timestamp,
                                                    boolean device8HasValue, float device8Value,
                                                    boolean device9HasValue, float device9Value,
                                                    boolean device10HasValue, float device10Value,
                                                    boolean outsideHasValue, float outsideValue) throws IOException {
        ArrayList<String> exceptionMessages = new ArrayList<>();
        int tries = 0;
        int maxTries = 3;
        while (tries <= 3) {
            tries++;
            try {
                insertSamples(table, timestamp,
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
        throw new IOException("Could not insert data to Table " + table +
                "\n Exceptions: " + String.join("\n", exceptionMessages));
    }

    private void insertSamples(String tableName, CharSequence timestamp,
                               boolean device8HasValue, float device8Value,
                               boolean device9HasValue, float device9Value,
                               boolean device10HasValue, float device10Value,
                               boolean outsideHasValue, float outsideValue) throws IOException {

        String insertQuery = "INSERT INTO Data." + tableName +
                " (Date, Bedroom, Living_room, Kids_room, Outside) VALUES (" +
                "TIMESTAMP '" + String.valueOf(timestamp) + " Europe/Zurich', " +
                (device8HasValue ? device8Value : "NULL") + ", " +
                (device9HasValue ? device9Value : "NULL") + ", " +
                (device10HasValue ? device10Value : "NULL") + ", " +
                (outsideHasValue ? outsideValue : "NULL") + ")";

        QueryRequest queryRequest = QueryRequest.builder(insertQuery).useLegacySql(false).build();
        final QueryResponse response = bigQueryApi.query(queryRequest);

        MyLog.d(TAG, "Inserted new samples data. Response: " + response);
    }

    public synchronized float queryAvg(String tableName) throws IOException {

        String readAvgQuery = "WITH MOVING_AVG AS " +
                "(SELECT " +
                "date_day, avg_inside, AVG(avg_inside) OVER (ORDER BY day RANGE BETWEEN 2 PRECEDING AND CURRENT ROW) AS mov_avg_3d " +
                "FROM (SELECT " +
                "DATE(Date) AS date_day, UNIX_DATE(DATE(date)) AS day, AVG((Bedroom + Living_room + Kids_room)/3) AS avg_inside " +
                "FROM Data." + tableName + " " +
                "WHERE Date > TIMESTAMP(DATE_SUB(CURRENT_DATE, INTERVAL 4 DAY)) " +
                "GROUP BY date_day, day" +
                ")" +
                ") " +
                "SELECT mov_avg_3d FROM MOVING_AVG,(SELECT MAX(date_day) as maxDate FROM MOVING_AVG) x " +
                "WHERE date_day = x.maxDate";

        QueryRequest queryRequest = QueryRequest.builder(readAvgQuery).useLegacySql(false).build();
        final QueryResponse response = bigQueryApi.query(queryRequest);

        MyLog.d(TAG, "Read average. Response: " + response);

        final double avg = response.result().values().iterator().next().get(0).doubleValue();
        return (float) avg;
    }

}

