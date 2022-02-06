package com.home.weatherstation.remote;

import com.google.cloud.AuthCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.QueryRequest;
import com.google.cloud.bigquery.QueryResponse;
import com.home.weatherstation.BuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class BigQueryProvider {

    private static final String TAG = BigQueryProvider.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    private static BigQueryProvider instance = null;

    static final String TABLE_TEMPERATURE = "Temperature";
    static final String TABLE_HUMIDITY = "Humidity";
    static final String TABLE_BATTERY = "Battery";
    static final String TABLE_PRECIPITATION = "Precipitation";
    static final String TABLE_SUNSHINE = "Sunshine";

    private final BigQuery bigQueryApi;

    private BigQueryProvider(BigQuery bigQueryApi) {

        this.bigQueryApi = bigQueryApi;
    }

    public static synchronized BigQueryProvider getInstance() {
        if (instance == null) {
            logger.trace(TAG, "Creating new singleton instance ...");

            try {
                InputStream inputStream = BigQueryProvider.class.getClassLoader().getResourceAsStream(BuildConfig.GOOGLE_CLOUD_AUTH_JSON_FILENAME);
                AuthCredentials authCredentials = AuthCredentials.createForJson(inputStream);
                BigQuery bigQueryApi =
                        BigQueryOptions.builder()
                                .authCredentials(authCredentials)
                                .projectId("weatherstation-1347")
                                .build().service();

                instance = new BigQueryProvider(bigQueryApi);
            } catch (IOException e) {
                logger.error("Could not create BigQuery API.", e);
            }

        } else {
            logger.trace("Return existing singleton instance ...");
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
            } catch (BigQueryException e) {
                logger.warn(tries + "s/" + maxTries + ": Failed to insert sample.", e);
                exceptionMessages.add(e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    logger.error("Could not sleep", e1);
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
                               boolean outsideHasValue, float outsideValue) {

        String insertQuery = "INSERT INTO Data." + tableName +
                " (Date, Bedroom, Living_room, Kids_room, Outside) VALUES (" +
                "TIMESTAMP '" + timestamp + " Europe/Zurich', " +
                (device8HasValue ? device8Value : "NULL") + ", " +
                (device9HasValue ? device9Value : "NULL") + ", " +
                (device10HasValue ? device10Value : "NULL") + ", " +
                (outsideHasValue ? outsideValue : "NULL") + ")";

        QueryRequest queryRequest = QueryRequest.builder(insertQuery).useLegacySql(false).build();
        final QueryResponse response = bigQueryApi.query(queryRequest);

        logger.debug("Inserted new samples data."); //"Response: " + response);
    }

    public synchronized float queryAvg(String tableName) {

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

        logger.debug("Read average."); //Response: " + response);

        final double avg = response.result().values().iterator().next().get(0).doubleValue();
        return (float) avg;
    }

}

