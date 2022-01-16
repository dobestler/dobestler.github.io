package com.home.weatherstation;

import java.util.Date;

public class Sample {
    static final float NOT_SET_FLOAT = -999f;
    static final int NOT_SET_INT = -999;

    private final Date timestamp;
    private final String deviceName;
    private final float temperature;
    private final int relativeHumidity;
    private final float sunshine;
    private final float precipitation;
    private final int batteryLevel;

    public Sample(final Date timestamp, String deviceName, final float temperature, final int relativeHumidity, final float precipitation, final float sunshine, final int batteryLevel) {
        this.timestamp = timestamp;
        this.deviceName = deviceName;
        this.temperature = temperature;
        this.relativeHumidity = relativeHumidity;
        this.precipitation = precipitation;
        this.sunshine = sunshine;
        this.batteryLevel = batteryLevel;
    }

    public static Sample createEmpty(final String nameIfEmpty) {
        return new Sample(new Date(), nameIfEmpty, Sample.NOT_SET_FLOAT, Sample.NOT_SET_INT, Sample.NOT_SET_FLOAT, Sample.NOT_SET_FLOAT, Sample.NOT_SET_INT);
    }

    public static boolean isNullOrEmpty(Sample sample) {
        return sample == null || sample.isEmpty();
    }

    private boolean isEmpty() {
        return !hasTempCurrent() && !hasRelativeHumidity(); // && !hasPrecipitation() && !hasSunshine() && !hasBatteryLevelCurrent();
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public float getTemperature() {
        return temperature;
    }

    public boolean hasTempCurrent() {
        return temperature != NOT_SET_FLOAT;
    }

    public int getRelativeHumidity() {
        return relativeHumidity;
    }

    public boolean hasRelativeHumidity() {
        return relativeHumidity != NOT_SET_INT;
    }

    public float getPrecipitation() {
        return precipitation;
    }

    public boolean hasPrecipitation() {
        return precipitation != NOT_SET_FLOAT;
    }

    public float getSunshine() {
        return sunshine;
    }

    public boolean hasSunshine() {
        return sunshine != NOT_SET_FLOAT;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public boolean hasBatteryLevelCurrent() {
        return batteryLevel != NOT_SET_INT;
    }

    @Override
    public String toString() {
        return "Sample{" +
                "timestamp=" + timestamp +
                ", deviceName='" + deviceName + '\'' +
                ", temperature=" + temperature +
                ", relativeHumidity=" + relativeHumidity +
                ", precipitation=" + precipitation +
                ", sunshine=" + sunshine +
                ", batteryLevel=" + batteryLevel +
                '}';
    }

}
