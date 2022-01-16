package com.home.weatherstation;

import com.home.weatherstation.smn.SmnData;
import com.home.weatherstation.smn.SmnRecord;
import com.welie.blessed.*;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;

public class ScannerService {

    private static final String TAG = ScannerService.class.getSimpleName();
    private static final Logger logger = LoggerFactory.getLogger(TAG);

    private static final int BLUE_MAESTRO_ID = 307;

    private static ScannerService instance;

    private static final String DEVICE_NO08_MAC_ADDRESS = "F0:E7:FA:CE:1F:D8"; // Bedroom
    private static final String DEVICE_NO09_MAC_ADDRESS = "FA:67:91:00:D7:B2"; // Living room
    private static final String DEVICE_NO10_MAC_ADDRESS = "DC:6C:14:1C:96:97"; // Kids' room

    // Temperature calibration shift
    private static final float DEVICE_NO8_TEMP_SHIFT_DEGREES = -0.1f; // new device
    private static final float DEVICE_NO9_TEMP_SHIFT_DEGREES = 0.1f;  // old device
    private static final float DEVICE_N10_TEMP_SHIFT_DEGREES = -0.2f;  // new device

    // Relative Humidity calibration multiplier (calibrated at 23-24deg and relHum 49-55%)
    private static final double DEVICE_NO8_RELHUM_CALIBRATION = 0.855d;
    private static final double DEVICE_NO9_RELHUM_CALIBRATION = 1.098d;
    private static final double DEVICE_N10_RELHUM_CALIBRATION = 1.000d;

    private static final String OPEN_DATA_URL = "https://data.geo.admin.ch/ch.meteoschweiz.messwerte-aktuell/VQHA80.csv";

    public static final String SAMPLE_NAME_DEVICE8 = "Device8";
    public static final String SAMPLE_NAME_DEVICE9 = "Device9";
    public static final String SAMPLE_NAME_DEVICE10 = "Device10";
    public static final String SAMPLE_NAME_OUTSIDE = "Outside";

    // Stops scanning after 180 seconds.
    private static final long SCAN_PERIOD = 180000;
    private final Runnable stopScanAndProcessRunnable = this::timeout;
    private ScheduledFuture<?> timeoutFuture;
    private final Handler queueHandler = new Handler("Scanner-Queue");

    private final BluetoothCentralManager central;

    // results from scanner, poor man's simple caching approach...
    private Sample deviceNr8 = null;
    private Sample deviceNr9 = null;
    private Sample deviceNr10 = null;

    // Ensures that only one scan is running at a time
    private boolean scanRunning = false;

    private ScannerService() {
        central = new BluetoothCentralManager(bluetoothCentralManagerCallback);
        central.setRssiThreshold((short) -120);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down ...");
            central.stopScan();
        }));
    }

    public static ScannerService getInstance() {
        if (instance == null) {
            instance = new ScannerService();
        }
        return instance;
    }

    public void scanAndUpload() {
        if (scanRunning) {
            logger.info("Already scanning. Ignoring this scan request.");
        } else {
            scanRunning = true;
            logger.info("Ensuring BT is powered and starting scan in 5s ...");
            central.adapterOn();
            queueHandler.postDelayed(this::scanLeDevice, 5000);
        }
    }

    private void restartBT() {
        queueHandler.postDelayed(() -> {
            logger.info("Disabling BT after waiting for 1s ...");
            central.adapterOff();
        }, 1000);

        queueHandler.postDelayed(() -> {
            logger.info("Re-enabling BT after waiting for 5s ...");
            central.adapterOn();
        }, 5000);
    }

    private void scanLeDevice() {
        logger.info("Start Scanning for devices for " + SCAN_PERIOD + "ms ...");
        scheduleTimeoutTimer();
        resetCachedSampleData();
        central.scanForPeripheralsWithAddresses(new String[]{DEVICE_NO08_MAC_ADDRESS, DEVICE_NO09_MAC_ADDRESS, DEVICE_NO10_MAC_ADDRESS});
    }

    private void scheduleTimeoutTimer() {
        cancelTimeoutTimer();
        timeoutFuture = queueHandler.postDelayed(stopScanAndProcessRunnable, SCAN_PERIOD);
    }

    private void cancelTimeoutTimer() {
        // cancel timeout timer
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    private void resetCachedSampleData() {
        deviceNr8 = null; // reset samples!
        deviceNr9 = null; // reset samples!
        deviceNr10 = null; // reset samples!
    }

    private boolean hasAllSampleData() {
        return deviceNr8 != null && deviceNr9 != null && deviceNr10 != null;
    }

    private void timeout() {
        logger.info("Scanning timed out.");
        stopScanAndProcessResults();
    }

    private void stopScanAndProcessResults() {
        scanRunning = false;
        central.stopScan();
        logger.info("Scanning done");
        process();
        //restartBT(); // This seemed to harm the stability on the Raspberry4
    }

    BluetoothCentralManagerCallback bluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {
        @Override
        public void onDiscoveredPeripheral(@NotNull BluetoothPeripheral peripheral, ScanResult scanResult) {
            logger.trace("onDiscoveredPeripheral: scanResult = " + scanResult.toString());
            cacheSample(scanResult);
        }

        private void cacheSample(ScanResult result) {
            Date now = new Date();
            String deviceAddress = result.getAddress();
            byte[] blueMaestroSpecificData = result.getManufacturerData().get(BLUE_MAESTRO_ID);
            if (blueMaestroSpecificData != null) {
                if (deviceNr8 == null && DEVICE_NO08_MAC_ADDRESS.equals(deviceAddress)) {
                    if (isDataFromAdvertisementPackage(blueMaestroSpecificData)) {
                        deviceNr8 = parseNewDevice(SAMPLE_NAME_DEVICE8, blueMaestroSpecificData, now, DEVICE_NO8_TEMP_SHIFT_DEGREES, DEVICE_NO8_RELHUM_CALIBRATION);
                        logger.info("Got Sample from Device No.8: " + deviceNr8);
                    }
                } else if (deviceNr9 == null && DEVICE_NO09_MAC_ADDRESS.equals(deviceAddress)) {
                    deviceNr9 = parse(SAMPLE_NAME_DEVICE9, blueMaestroSpecificData, now, DEVICE_NO9_TEMP_SHIFT_DEGREES, DEVICE_NO9_RELHUM_CALIBRATION);
                    logger.info("Got Sample from Device No.9:" + deviceNr9);
                } else if (deviceNr10 == null && DEVICE_NO10_MAC_ADDRESS.equals(deviceAddress)) {
                    if (isDataFromAdvertisementPackage(blueMaestroSpecificData)) {
                        deviceNr10 = parseNewDevice(SAMPLE_NAME_DEVICE10, blueMaestroSpecificData, now, DEVICE_N10_TEMP_SHIFT_DEGREES, DEVICE_N10_RELHUM_CALIBRATION);
                        logger.info("Got Sample from Device No.10:" + deviceNr10);
                    }
                }
            }

            if (hasAllSampleData() && timeoutFuture != null) {
                cancelTimeoutTimer();
                stopScanAndProcessResults();
            }
        }

        /*
            New Blue Maestro Devices are advertising the relevant portion of the data differently than the old one. The data
            for current measurements is part of the manufacturer specific data of the ADVERTISEMENT package as opposed
            the the SCAN RESPONSE package for the old devices. Since the order of the packages seems not guaranteed we need
            to introspect the data. The relevant data in the ADVERTISEMENT has 14 bytes and the other data in the SCAN RESPONSE
            has 26 bytes.

            Output of 'sudo btmon' for Device_8 (NEW DEVICE):
            > HCI Event: LE Meta Event (0x3e) plen 43                                                                                                                                                                                                                                               #1910 [hci0] 1398.219565
                LE Advertising Report (0x02)
                Num reports: 1
                Event type: Connectable undirected - ADV_IND (0x00)          <<<<<<< ADVERTISEMENT PACKAGE
                Address type: Random (0x01)
                Address: F0:E7:FA:CE:1F:D8 (Static)
                Data length: 31
                Flags: 0x06
                  LE General Discoverable Mode
                  BR/EDR Not Supported
                Company: Blue Maestro Limited (307)
                  Data: 16600e1005ff00ec027c00a40100                         <<<<<<< Data with current measurement values
                Name (complete): TempHum3
                RSSI: -76 dBm (0xb4)
            > HCI Event: LE Meta Event (0x3e) plen 42                                                                                                                                                                                                                                               #1911 [hci0] 1398.220523
                LE Advertising Report (0x02)
                Num reports: 1
                Event type: Scan response - SCAN_RSP (0x04)                  <<<<<<< SCAN RESPONSE PACKAGE
                Address type: Random (0x01)
                Address: F0:E7:FA:CE:1F:D8 (Static)
                Data length: 30
                Company: Blue Maestro Limited (307)
                  Data: 010802d700d8019600f302bd00be00e90258009a00ec028400a5 <<<<<<<< Other measurement data we are not currently interested in
                RSSI: -76 dBm (0xb4)

            ------------- Device_9 (OLD DEVICE)
            > HCI Event: LE Meta Event (0x3e) plen 31                                                                                                                                                                                                                                               #1903 [hci0] 1397.885670
                LE Advertising Report (0x02)
                Num reports: 1
                Event type: Connectable undirected - ADV_IND (0x00)          <<<<<<< ADVERTISEMENT PACKAGE (without manufacturer data)
                Address type: Random (0x01)
                Address: FA:67:91:00:D7:B2 (Static)
                Data length: 19
                Name (complete): Tempo v3
                Flags: 0x06
                  LE General Discoverable Mode
                  BR/EDR Not Supported
                16-bit Service UUIDs (partial): 2 entries
                  Battery Service (0x180f)
                  Device Information (0x180a)
                RSSI: -73 dBm (0xb7)
            > HCI Event: LE Meta Event (0x3e) plen 34                                                                                                                                                                                                                                                   #10 [hci0] 50.573666
                LE Advertising Report (0x02)
                Num reports: 1
                Event type: Scan response - SCAN_RSP (0x04)                  <<<<<<< SCAN RESPONSE PACKAGE
                Address type: Random (0x01)
                Address: FA:67:91:00:D7:B2 (Static)
                Data length: 22
                Company: Blue Maestro Limited (307)
                  Data: 01f000f000f0002aba03b2d7009167fa0700                 <<<<<<< Data with current measurement values
                RSSI: -81 dBm (0xaf)
         */
        private boolean isDataFromAdvertisementPackage(byte[] blueMaestroSpecificData) {
            return blueMaestroSpecificData.length == 14;
        }
    };

    private void process() {
        Date timestamp = new Date();
        Storage.storeLastScanTime(timestamp.getTime());

        final Sample outside = fetchCurrentConditionsOutsideOpenDataDirectly();

        logger.info("Processing samples : " + deviceNr8 + " -- " + deviceNr9 + " -- " + deviceNr10);
        new UploadService().startUpload(timestamp,
                getSampleOrCreateEmpty(deviceNr8, SAMPLE_NAME_DEVICE8),
                getSampleOrCreateEmpty(deviceNr9, SAMPLE_NAME_DEVICE9),
                getSampleOrCreateEmpty(deviceNr10, SAMPLE_NAME_DEVICE10),
                getSampleOrCreateEmpty(outside, SAMPLE_NAME_OUTSIDE));
    }

    private Sample getSampleOrCreateEmpty(final Sample sample, String nameIfEmpty) {
        return sample == null ? Sample.createEmpty(nameIfEmpty) : sample;
    }

    // Old (bigger) devices
    @Nullable
    private Sample parse(String name, byte[] manufacturerData, Date date, float tempCalibrationShift,
                         double relhumCalibrationMultiplier) {
        if (manufacturerData == null) {
            logger.warn("ManufacturerSpecificData is null");
            return null;
        }

        ByteBuffer bytes = ByteBuffer.wrap(manufacturerData).order(ByteOrder.LITTLE_ENDIAN);

        float tempCurrent = ((float) bytes.getShort(3)) / 10.0f;
        byte humidity = bytes.get(7);
        float precipitation = Sample.NOT_SET_FLOAT;
        float sunshine = Sample.NOT_SET_FLOAT;
        int battery = Sample.NOT_SET_INT;

        return new Sample(date, name,
                round(tempCurrent + tempCalibrationShift, 1),
                (int) round((float) (humidity * relhumCalibrationMultiplier), 0),
                precipitation, sunshine, battery);
    }

    @Nullable
    private Sample parseNewDevice(String name, byte[] manufacturerData, Date date, float tempCalibrationShift,
                          double relhumCalibrationMultiplier) {
        if (manufacturerData == null) {
            logger.warn("ManufacturerSpecificData is null");
            return null;
        }

        ByteBuffer bytes = ByteBuffer.wrap(manufacturerData).order(ByteOrder.BIG_ENDIAN);

        int battery = bytes.get(1);
        float tempCurrent = ((float) bytes.getShort(6)) / 10.0f;
        float humidity = ((float) bytes.getShort(8)) / 10.0f;
        float precipitation = Sample.NOT_SET_FLOAT;
        float sunshine = Sample.NOT_SET_FLOAT;

        return new Sample(date, name,
                round(tempCurrent + tempCalibrationShift, 1),
                (int) round((float)(humidity * relhumCalibrationMultiplier), 0),
                precipitation, sunshine, battery);
    }

    public static float round(float f, int decimalPlace) {
        return new BigDecimal(Float.toString(f)).setScale(decimalPlace, RoundingMode.HALF_UP).floatValue();
    }


    private static Sample fetchCurrentConditionsOutsideOpenDataDirectly() {
        logger.debug("Fetching Outside Conditions ...");

        try {
            URL url = new URL(OPEN_DATA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            logger.debug("Fetch Outside Conditions - Response Code: " + conn.getResponseCode());
            InputStream in = new BufferedInputStream(conn.getInputStream(), 1024);
            String response = IOUtils.toString(in, StandardCharsets.UTF_8);

            String stationCode = "SMA";
            SmnRecord currentObservation = new SmnData(response).getRecordFor(stationCode);
            Date d = parseDate(currentObservation.getDateTime());
            final String temp = currentObservation.getTemperature();
            final String humidity = currentObservation.getHumidity();
            final String precipationS = currentObservation.getPrecipitation();
            final String sunshineS = currentObservation.getSunshine();

            if (nullOrEmpty(temp) && nullOrEmpty(humidity) && nullOrEmpty(precipationS) && nullOrEmpty(sunshineS)) {
                throw new Exception("No Temp, no Humidity, no Precipitation available for station with Code = " + stationCode);
            } else {
                float tempCurrent = nullOrEmpty(temp) ? Sample.NOT_SET_FLOAT : Float.parseFloat(temp);
                int relHumid = nullOrEmpty(humidity) ? Sample.NOT_SET_INT : Math.round(Float.parseFloat(humidity));
                float precipitation = nullOrEmpty(precipationS) ? Sample.NOT_SET_FLOAT : Float.parseFloat(precipationS);
                float sunshine = nullOrEmpty(sunshineS) ? Sample.NOT_SET_FLOAT : Float.parseFloat(sunshineS);
                return new Sample(d, SAMPLE_NAME_OUTSIDE, tempCurrent, relHumid, precipitation, sunshine, Sample.NOT_SET_INT);
            }
        } catch (Exception e) {
            logger.warn("Could not get current outside conditions.", e);
            return Sample.createEmpty(SAMPLE_NAME_OUTSIDE);
        }
    }

    private static boolean nullOrEmpty(String s) {
        return s == null || s.trim().equals("");
    }

    private static Date parseDate(String dateString) {
        DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return utcFormat.parse(dateString);
        } catch (ParseException e) {
            new ExceptionReporter().sendException(e);
            return new Date();
        }
    }

}