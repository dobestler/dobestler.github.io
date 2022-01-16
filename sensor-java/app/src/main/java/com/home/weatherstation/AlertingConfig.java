package com.home.weatherstation;

import java.io.Serializable;
import java.text.DecimalFormat;

class AlertingConfig implements Serializable {

    private float upperThresholdHumidity;
    private float lowerThresholdHumidity;

    public AlertingConfig() {
        super();
        upperThresholdHumidity = 58.0f; // Default
        lowerThresholdHumidity = 43.0f; // Default
    }

    public float getLowerThresholdHumidity() {
        return lowerThresholdHumidity;
    }

    public void setLowerThresholdHumidity(float lowerThresholdHumidity) {
        this.lowerThresholdHumidity = lowerThresholdHumidity;
    }

    public float getUpperThresholdHumidity() {
        return upperThresholdHumidity;
    }

    public void setUpperThresholdHumidity(float upperThresholdHumidity) {
        this.upperThresholdHumidity = upperThresholdHumidity;
    }

    @Override
    public String toString() {
        return String.format("%s,%s", new DecimalFormat("#").format(lowerThresholdHumidity), new DecimalFormat("#").format(upperThresholdHumidity));
    }
}
