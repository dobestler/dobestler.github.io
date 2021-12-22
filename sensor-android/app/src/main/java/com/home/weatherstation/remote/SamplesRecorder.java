package com.home.weatherstation.remote;

import android.content.Context;

import com.home.weatherstation.Sample;
import com.home.weatherstation.util.MyLog;

import java.io.IOException;
import java.util.Date;

public class SamplesRecorder {

    private static final String TAG = SamplesRecorder.class.getSimpleName();

    private final BigQueryProvider bigQueryProvider;

    public SamplesRecorder(Context context) {
        this.bigQueryProvider = BigQueryProvider.getInstance(context.getApplicationContext());
    }

    public void record(Date timestamp, Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample outside) throws IOException {
        CharSequence timestampValue = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", timestamp);

        MyLog.i(TAG, "Recording TEMPERATURE samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_TEMPERATURE,
                timestampValue.toString(),
                deviceNo8.hasTempCurrent(), deviceNo8.getTemperature(),
                deviceNo9.hasTempCurrent(), deviceNo9.getTemperature(),
                deviceNo10.hasTempCurrent(), deviceNo10.getTemperature(),
                outside.hasTempCurrent(), outside.getTemperature());

        MyLog.i(TAG, "Recording HUMIDITY samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_HUMIDITY,
                timestampValue.toString(),
                deviceNo8.hasRelativeHumidity(), deviceNo8.getRelativeHumidity(),
                deviceNo9.hasRelativeHumidity(), deviceNo9.getRelativeHumidity(),
                deviceNo10.hasRelativeHumidity(), deviceNo10.getRelativeHumidity(),
                outside.hasRelativeHumidity(), outside.getRelativeHumidity());

        MyLog.i(TAG, "Recording PRECIPITATION samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_PRECIPITATION,
                timestampValue.toString(),
                deviceNo8.hasPrecipitation(), deviceNo8.getPrecipitation(), //not set
                deviceNo9.hasPrecipitation(), deviceNo9.getPrecipitation(), //not set
                deviceNo10.hasPrecipitation(), deviceNo10.getPrecipitation(), //not set
                outside.hasPrecipitation(), outside.getPrecipitation());

        MyLog.i(TAG, "Recording SUNSHINE samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_SUNSHINE,
                timestampValue.toString(),
                deviceNo8.hasSunshine(), deviceNo8.getSunshine(), //not set
                deviceNo9.hasSunshine(), deviceNo9.getSunshine(), //not set
                deviceNo10.hasSunshine(), deviceNo10.getSunshine(), //not set
                outside.hasSunshine(), outside.getSunshine());

        MyLog.i(TAG, "Recording BATTERY samples ...");
        bigQueryProvider.insertSamplesWithRetry(
                BigQueryProvider.TABLE_BATTERY,
                timestampValue.toString(),
                deviceNo8.hasBatteryLevelCurrent(), deviceNo8.getBatteryLevel(),
                deviceNo9.hasBatteryLevelCurrent(), deviceNo9.getBatteryLevel(),
                deviceNo10.hasBatteryLevelCurrent(), deviceNo10.getBatteryLevel(),
                outside.hasBatteryLevelCurrent(), outside.getBatteryLevel());

    }

    public float queryAvgHumidity() {
        MyLog.i(TAG, "Reading average humidity data ...");
        return bigQueryProvider.queryAvg(BigQueryProvider.TABLE_HUMIDITY);
    }
}