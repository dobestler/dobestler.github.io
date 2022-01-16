package com.home.weatherstation;

import com.google.cloud.bigquery.BigQueryException;
import com.home.weatherstation.remote.SamplesRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends an alert if the average value for the last 7 days is below or above the thresholds.
 */
public class ThresholdCheckerService {

    private static final String TAG = ThresholdCheckerService.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    private final AlertingConfig config;

    public ThresholdCheckerService(final AlertingConfig config) {
        super();
        this.config = config;
    }

    public void checkThresholds() {
        int lastXdays = -4;

        try {
            float averageHum = new SamplesRecorder().queryAvgHumidity();

            long thresholdExceededHumidityFromStorage = Storage.readThresholdExceededHumidity();

            Storage.storeAverageHumidity(averageHum);
            final ExceptionReporter exceptionReporter = new ExceptionReporter();
            if (averageHum < config.getLowerThresholdHumidity() || averageHum > config.getUpperThresholdHumidity()) {
                long now = System.currentTimeMillis();
                if (thresholdExceededHumidityFromStorage == -1) {
                    Storage.storeThresholdExceededHumidity(now);
                    exceptionReporter.sendThresholdExceededAlert(averageHum, lastXdays, config.getLowerThresholdHumidity(), config.getUpperThresholdHumidity());
                }
            } else {
                if (Storage.readThresholdExceededHumidity() > -1) {
                    exceptionReporter.sendThresholdRecoveredAlert(averageHum, lastXdays, config.getLowerThresholdHumidity(), config.getUpperThresholdHumidity());
                }
                Storage.removeThresholdExceededHumidity();
            }
        } catch (NumberFormatException e) {
            logger.warn("Not enough data to calculate 4d average -> 'n/a' instead of float");
        } catch (BigQueryException e) {
            new ExceptionReporter().sendException(e);
        }
    }

}