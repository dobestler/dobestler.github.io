package com.home.weatherstation.remote;

import com.home.weatherstation.Sample;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SamplesRecorder {

    private static final String TAG = SamplesRecorder.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final BigQueryProvider bigQueryProvider;

    public SamplesRecorder() {
        this.bigQueryProvider = BigQueryProvider.getInstance();
    }

    public void record(@NotNull Date timestamp, @NotNull Sample deviceNo8, @NotNull Sample deviceNo9, @NotNull Sample deviceNo10, @NotNull Sample outside) throws IOException {
        String timestampValue = DATE_FORMAT.format(timestamp);

        logger.info("Recording TEMPERATURE samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_TEMPERATURE,
                timestampValue,
                deviceNo8.hasTempCurrent(), deviceNo8.getTemperature(),
                deviceNo9.hasTempCurrent(), deviceNo9.getTemperature(),
                deviceNo10.hasTempCurrent(), deviceNo10.getTemperature(),
                outside.hasTempCurrent(), outside.getTemperature());

        logger.info("Recording HUMIDITY samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_HUMIDITY,
                timestampValue,
                deviceNo8.hasRelativeHumidity(), deviceNo8.getRelativeHumidity(),
                deviceNo9.hasRelativeHumidity(), deviceNo9.getRelativeHumidity(),
                deviceNo10.hasRelativeHumidity(), deviceNo10.getRelativeHumidity(),
                outside.hasRelativeHumidity(), outside.getRelativeHumidity());

        logger.info("Recording PRECIPITATION samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_PRECIPITATION,
                timestampValue,
                deviceNo8.hasPrecipitation(), deviceNo8.getPrecipitation(), //not set
                deviceNo9.hasPrecipitation(), deviceNo9.getPrecipitation(), //not set
                deviceNo10.hasPrecipitation(), deviceNo10.getPrecipitation(), //not set
                outside.hasPrecipitation(), outside.getPrecipitation());

        logger.info("Recording SUNSHINE samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_SUNSHINE,
                timestampValue,
                deviceNo8.hasSunshine(), deviceNo8.getSunshine(), //not set
                deviceNo9.hasSunshine(), deviceNo9.getSunshine(), //not set
                deviceNo10.hasSunshine(), deviceNo10.getSunshine(), //not set
                outside.hasSunshine(), outside.getSunshine());

        logger.info("Recording BATTERY samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_BATTERY,
                timestampValue,
                deviceNo8.hasBatteryLevelCurrent(), deviceNo8.getBatteryLevel(),
                deviceNo9.hasBatteryLevelCurrent(), deviceNo9.getBatteryLevel(),
                deviceNo10.hasBatteryLevelCurrent(), deviceNo10.getBatteryLevel(),
                outside.hasBatteryLevelCurrent(), outside.getBatteryLevel());

    }

    public float queryAvgHumidity() {
        logger.info("Reading average humidity data ...");
        return bigQueryProvider.queryAvg(BigQueryProvider.TABLE_HUMIDITY);
    }
}