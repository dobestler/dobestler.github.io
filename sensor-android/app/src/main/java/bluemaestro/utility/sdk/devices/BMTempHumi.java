/*
 * Copyright (c) 2016, Blue Maestro
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package bluemaestro.utility.sdk.devices;

/**
 * Created by Garrett on 17/08/2016.
 */
public class BMTempHumi { //extends BMDevice {

    /**
     * Device mode
     */
    protected byte mode;

    // Battery level
    private int battery;

    // Current temperature, humidity, and dew point
    private double currTemperature;
    private double currHumidity;
    private double currDewPoint;

    // Number of threshold breaches
    private int numBreach;

    // Highest temperature and humidity recorded
    private double highTemperature;
    private double highHumidity;

    // Lowest temperature and humidity recorded
    private double lowTemperature;
    private double lowHumidity;

    // Highest temperature, humidity, and dew point recorded in last 24 hours
    private double high24Temperature;
    private double high24Humidity;
    private double high24DewPoint;

    // Lowest temperature, humidity, and dew point recorded in last 24 hours
    private double low24Temperature;
    private double low24Humidity;
    private double low24DewPoint;

    // Average temperature, humidity, and dew point recorded in last 24 hours
    private double avg24Temperature;
    private double avg24Humidity;
    private double avg24DewPoint;


    public BMTempHumi(byte[] mData, byte[] sData) {
        super();

        this.battery = mData[4];

        this.currTemperature = convertToUInt16(mData[9], mData[10]) / 10.0;
        this.currHumidity = convertToUInt16(mData[11], mData[12]) / 10.0;
        this.currDewPoint = convertToUInt16(mData[13], mData[14]) / 10.0;

        this.mode = mData[15];

        this.numBreach = mData[16];

        this.highTemperature = convertToUInt16(sData[3], sData[4]) / 10.0;
        this.highHumidity = convertToUInt16(sData[5], sData[6]) / 10.0;

        this.lowTemperature = convertToUInt16(sData[7], sData[8]) / 10.0;
        this.lowHumidity = convertToUInt16(sData[9], sData[10]) / 10.0;

        this.high24Temperature = convertToUInt16(sData[11], sData[12]) / 10.0;
        this.high24Humidity = convertToUInt16(sData[13], sData[14]) / 10.0;
        this.high24DewPoint = convertToUInt16(sData[15], sData[16]) / 10.0;

        this.low24Temperature = convertToUInt16(sData[17], sData[18]) / 10.0;
        this.low24Humidity = convertToUInt16(sData[19], sData[20]) / 10.0;
        this.low24DewPoint = convertToUInt16(sData[21], sData[22]) / 10.0;

        this.avg24Temperature = convertToUInt16(sData[23], sData[24]) / 10.0;
        this.avg24Humidity = convertToUInt16(sData[25], sData[26]) / 10.0;
        this.avg24DewPoint = convertToUInt16(sData[27], sData[28]) / 10.0;
    }

    /**
     * Convert two bytes to unsigned int 16
     *
     * @param first
     * @param second
     * @return
     */
    protected static final int convertToUInt16(byte first, byte second) {
        int value = (int) first & 0xFF;
        value *= 256;
        value += (int) second & 0xFF;
        value -= (value > 32768) ? 65536 : 0;
        return value;
    }

    public int getBatteryLevel() {
        return battery;
    }

    public double getCurrentTemperature() {
        return currTemperature;
    }

    public double getCurrentHumidity() {
        return currHumidity;
    }

    public double getCurrentDewPoint() {
        return currDewPoint;
    }

    public boolean isInAeroplaneMode() {
        return mode % 100 == 5;
    }

    public boolean isInFahrenheit() {
        return mode >= 100;
    }

    public String getTempUnits() {
        return isInFahrenheit() ? "°F" : "°C";
    }

    public int getNumBreach() {
        return numBreach;
    }

    public double getHighestTemperature() {
        return highTemperature;
    }

    public double getHighestHumidity() {
        return highHumidity;
    }

    public double getLowestTemperature() {
        return lowTemperature;
    }

    public double getLowestHumidity() {
        return lowHumidity;
    }

    public double getHighest24Temperature() {
        return high24Temperature;
    }

    public double getHighest24Humidity() {
        return high24Humidity;
    }

    public double getHighest24DewPoint() {
        return high24DewPoint;
    }

    public double getLowest24Temperature() {
        return low24Temperature;
    }

    public double getLowest24Humidity() {
        return low24Humidity;
    }

    public double getLowest24DewPoint() {
        return low24DewPoint;
    }

    public double getAverage24Temperature() {
        return avg24Temperature;
    }

    public double getAverage24Humidity() {
        return avg24Humidity;
    }

    public double getAverage24DewPoint() {
        return avg24DewPoint;
    }

}
