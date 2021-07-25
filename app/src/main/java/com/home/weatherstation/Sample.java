package com.home.weatherstation;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

public class Sample implements Parcelable {
    static final float NOT_SET_FLOAT = -999f;
    static final int NOT_SET_INT = -999;

    private Date timestamp;
    private String deviceName;
    private float temperature;
    private int relativeHumidity;
    private float sunshine;
    private float precipitation;
    private int batteryLevel;

    public Sample(final Date timestamp, String deviceName, final float temperature, final int relativeHumidity, final float precipitation, final float sunshine, final int batteryLevel) {
        this.timestamp = timestamp;
        this.deviceName = deviceName;
        this.temperature = temperature;
        this.relativeHumidity = relativeHumidity;
        this.precipitation = precipitation;
        this.sunshine = sunshine;
        this.batteryLevel = batteryLevel;
    }

    public Sample(Parcel in) {
        readFromParcel(in);
    }


    public static final Creator<Sample> CREATOR = new Creator<Sample>() {
        @Override
        public Sample createFromParcel(Parcel in) {
            return new Sample(in);
        }

        @Override
        public Sample[] newArray(int size) {
            return new Sample[size];
        }
    };

    public Date getTimestamp() {
        return timestamp;
    }

    public String getDeviceName() {
        return deviceName;
    }

    // could contain something like 23.800001, make sure to limit precision if needed
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timestamp.getTime());
        dest.writeString(deviceName);
        dest.writeFloat(temperature);
        dest.writeInt(relativeHumidity);
        dest.writeFloat(precipitation);
        dest.writeFloat(sunshine);
        dest.writeInt(batteryLevel);
    }

    private void readFromParcel(Parcel in) {
        // We just need to read back each field in the order that it was written to the parcel
        timestamp = new Date(in.readLong());
        deviceName = in.readString();
        temperature = in.readFloat();
        relativeHumidity = in.readInt();
        precipitation = in.readFloat();
        sunshine = in.readFloat();
        batteryLevel = in.readInt();
    }

}
