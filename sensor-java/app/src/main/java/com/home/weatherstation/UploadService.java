package com.home.weatherstation;

import com.home.weatherstation.remote.SamplesRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class UploadService {

    private static final String TAG = UploadService.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    private static final long ALERT_AFTER_INCOMPLETE_SAMPLES_IN_A_ROW = 3;

    public UploadService() {
        super();
    }

    public void startUpload(final Date timestamp, final Sample sampleDevice8, final Sample sampleDevice9, final Sample sampleDevice10, final Sample sampleOutside) {
        if (hasAllSampleData(sampleDevice8, sampleDevice9, sampleDevice10, sampleOutside)) {
            logger.info("Got samples from all BT Devices and Outside");
            Storage.storeLastSuccessfulScanTime(timestamp.getTime());
            Storage.removeIncompleteScans(sampleDevice8.getDeviceName(), sampleDevice9.getDeviceName(), sampleDevice10.getDeviceName(), sampleOutside.getDeviceName()); // reset
            upload(timestamp, sampleDevice8, sampleDevice9, sampleDevice10, sampleOutside);
        } else {
            handleIncompleteScan(sampleDevice8, sampleDevice9, sampleDevice10, sampleOutside);

            if (hasAnySampleData(sampleDevice8, sampleDevice9, sampleDevice10, sampleOutside)) {
                upload(timestamp, sampleDevice8, sampleDevice9, sampleDevice10, sampleOutside);
            } else {
                logger.warn("Did not receive any results!");
            }
        }
    }

    private void upload(Date timestamp, Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample sampleOutside) {
        try {
            new SamplesRecorder().record(timestamp, deviceNo8, deviceNo9, deviceNo10, sampleOutside);
            Storage.storeLastUploadTime(System.currentTimeMillis());
        } catch (Exception e) {
            new ExceptionReporter().sendException(e);
            logger.error("Could not record samples.", e);
        }
    }

    private boolean hasAllSampleData(Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample sampleOutside) {
        return
                hasTempAndHumidity(deviceNo8) && deviceNo8.hasBatteryLevelCurrent() &&
                hasTempAndHumidity(deviceNo9) /*&& deviceNo9.hasBatteryLevelCurrent()*/ &&
                hasTempAndHumidity(deviceNo10) && deviceNo10.hasBatteryLevelCurrent() &&
                hasTempAndHumidity(sampleOutside) && sampleOutside.hasPrecipitation();
    }

    private boolean hasTempAndHumidity(Sample sample) {
        return sample != null && sample.hasTempCurrent() && sample.hasRelativeHumidity();
    }

    private boolean hasAnySampleData(Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample sampleOutside) {
        return
                deviceNo8 != null && (deviceNo8.hasTempCurrent() || deviceNo8.hasRelativeHumidity() || deviceNo8.hasBatteryLevelCurrent()) ||
                deviceNo9 != null && (deviceNo9.hasTempCurrent() && deviceNo9.hasRelativeHumidity() /*&& deviceNo9.hasBatteryLevelCurrent()*/) ||
                deviceNo10 != null && (deviceNo10.hasTempCurrent() && deviceNo10.hasRelativeHumidity() && deviceNo10.hasBatteryLevelCurrent()) ||
                sampleOutside != null && (sampleOutside.hasTempCurrent() && sampleOutside.hasRelativeHumidity() && sampleOutside.hasPrecipitation());
    }



    private void handleIncompleteScan(Sample deviceNo8, Sample deviceNo9, Sample deviceNo10, Sample sampleOutside) {
        if (!hasTempAndHumidity(deviceNo8)) handleIncompleteScan(ScannerService.SAMPLE_NAME_DEVICE8);
        if (!hasTempAndHumidity(deviceNo9)) handleIncompleteScan(ScannerService.SAMPLE_NAME_DEVICE9);
        if (!hasTempAndHumidity(deviceNo10)) handleIncompleteScan(ScannerService.SAMPLE_NAME_DEVICE10);
        if (!hasTempAndHumidity(sampleOutside)) handleIncompleteScan(ScannerService.SAMPLE_NAME_OUTSIDE);
    }

    private void handleIncompleteScan(String deviceName) {
        logger.warn("Missing scan result for " + deviceName);
        long incompleteScans = Storage.readIncompleteScans(deviceName);
        incompleteScans++;
        Storage.storeIncompleteScans(incompleteScans, deviceName);
        logger.warn("Incomplete results to upload = " + incompleteScans + " of max " + ALERT_AFTER_INCOMPLETE_SAMPLES_IN_A_ROW);
        if (incompleteScans == ALERT_AFTER_INCOMPLETE_SAMPLES_IN_A_ROW) {
            new ExceptionReporter().sendIncompleteScansAlert(incompleteScans, deviceName);
        }
    }

}