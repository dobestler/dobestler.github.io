package com.home.weatherstation;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.creativityapps.gmailbackgroundlibrary.BackgroundMail;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import bluemaestro.utility.sdk.ble.ScanRecordParser;
import bluemaestro.utility.sdk.devices.BMTempHumi;

public class ScannerService extends Service {
    private static final int NOTIFICATION_ID = 101;

    private static final String START_SCHEDULER = "com.home.weatherstation.action.start_scheduled_scans";
    private static final String STOP_SCHEDULER = "com.home.weatherstation.action.stop_scheduled_scans";
    private static final String SCAN_AND_UPLOAD = "com.home.weatherstation.action.scan_and_upload";

    private static final String TAG = ScannerService.class.getSimpleName();

    private static final String DEVICE_NO08_MAC_ADDRESS = "F0:E7:FA:CE:1F:D8"; // Bedroom
    private static final String DEVICE_NO09_MAC_ADDRESS = "FA:67:91:00:D7:B2"; // Living room
    private static final String DEVICE_NO10_MAC_ADDRESS = "DC:6C:14:1C:96:97"; // Kids' room

    // Temperature calibration shift
    private static final float DEVICE_NO8_TEMP_SHIFT_DEGREES = -0.1f; // new device
    private static final float DEVICE_NO9_TEMP_SHIFT_DEGREES = 0.1f;  // old device
    private static final float DEVICE_N10_TEMP_SHIFT_DEGREES = 0.0f;  // new device

    // Relative Humidity calibration multiplier
    // (calibrated at 23.9deg and relHum 75%)
    //private static final double DEVICE_NO8_RELHUM_CALIBRATION = 0.89d;
    //private static final double DEVICE_NO9_RELHUM_CALIBRATION = 1.04d;
    //private static final double DEVICE_N10_RELHUM_CALIBRATION = 1.01d;
    // (calibrated at 23-24deg and relHum 49-55%)
    private static final double DEVICE_NO8_RELHUM_CALIBRATION = 0.906d;
    private static final double DEVICE_NO9_RELHUM_CALIBRATION = 1.098d;
    private static final double DEVICE_N10_RELHUM_CALIBRATION = 1.015d;


    private static final long MAX_INOMPLETE_SAMPLING_ATTEMPTS = 3;

    private BluetoothAdapter mBluetoothAdapter;
    private ScanSettings settings;
    private final List<ScanFilter> scanFilters = Collections.singletonList(new ScanFilter.Builder().build());

    private Handler mHandler;

    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;

    // results from scanner, poor mans simple caching approach...
    private Sample deviceNr8 = null;
    private Sample deviceNr9 = null;
    private Sample deviceNr10 = null;

    // Stops scanning after 20 seconds.
    private static final long SCAN_PERIOD = 20000;
    private final Runnable stopScanAndProcessRunnable = new Runnable() {
        @Override
        public void run() {
            stopScanAndProcessResults();
        }
    };

    private ServiceHelper serviceHelper;

    public static Intent buildStartSchedulerIntent(Context context) {
        Intent serviceIntent = new Intent(context, ScannerService.class);
        serviceIntent.setAction(ScannerService.START_SCHEDULER);
        return serviceIntent;
    }

    public static Intent buildStopSchedulerIntent(Context context) {
        Intent serviceIntent = new Intent(context, ScannerService.class);
        serviceIntent.setAction(ScannerService.STOP_SCHEDULER);
        return serviceIntent;
    }

    private static Intent buildScanAndUploadAndScheduleNextIntent(Context context) {
        Intent serviceIntent = buildScanAndUploadIntent(context);
        serviceIntent.putExtra("schedule_next", true);
        return serviceIntent;
    }

    public static Intent buildScanAndUploadIntent(Context context) {
        Intent serviceIntent = new Intent(context, ScannerService.class);
        serviceIntent.setAction(ScannerService.SCAN_AND_UPLOAD);
        return serviceIntent;
    }

    public ScannerService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onCreate() {
        serviceHelper = new ServiceHelper();

        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        mHandler = new Handler();

        mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                //.setReportDelay(500)
                .build();


        startForeground(NOTIFICATION_ID, serviceHelper.createNotification(this, NotificationManager.IMPORTANCE_MIN, getNotificationText(), true));

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action;
        boolean scheduleNext;

        if (intent == null) {
            action = START_SCHEDULER;
            scheduleNext = false;
        } else {
            action = intent.getAction();
            scheduleNext = intent.hasExtra("schedule_next");
        }

        if (START_SCHEDULER.equals(action)) {
            scheduleNextScan();
        } else if (STOP_SCHEDULER.equals(action)) {
            cancelNextScan();
        } else if (SCAN_AND_UPLOAD.equals(action)) {
            if (scheduleNext) {
                scheduleNextScan();
            }
            scanAndUpload();
        }

        // Update notification
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID,
                        serviceHelper.createNotification(
                                this, NotificationManager.IMPORTANCE_MIN,
                                getNotificationText(), true));


        return START_REDELIVER_INTENT;
    }


    private void scheduleNextScan() {
        Log.i(TAG, "Scheduling next scan ...");
        alarmIntent = serviceHelper.getForegroundServicePendingIntent(this, buildScanAndUploadAndScheduleNextIntent(this));
        long triggerTime = calculateNextTwentyMinsInMillis();
        alarmMgr.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerTime, PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT)), alarmIntent);
        Log.i(TAG, "Next scan scheduled at " + new Date(triggerTime));
        Storage.storeNextScheduledScanTime(this, triggerTime);
    }

    private static long calculateNextTwentyMinsInMillis() {
        Calendar cal = Calendar.getInstance();
        int currentHourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        int currentMinuteOfHour = cal.get(Calendar.MINUTE);

        cal.set(Calendar.MINUTE, 0); // reset to 00m:00s
        cal.set(Calendar.SECOND, 0);

        if (currentMinuteOfHour >= 0 && currentMinuteOfHour < 20) {
            cal.add(Calendar.MINUTE, 20);
        } else if (currentMinuteOfHour >= 20 && currentMinuteOfHour <= 39) {
            cal.add(Calendar.MINUTE, 40);
        } else if (currentMinuteOfHour >= 40 && currentMinuteOfHour <= 59) {
            cal.add(Calendar.HOUR, 1);
        } else {
            throw new IllegalArgumentException("currentHour=" + currentHourOfDay + ", currentMin=" + currentMinuteOfHour);
        }

        return cal.getTimeInMillis();
    }

    private void cancelNextScan() {
        if (alarmIntent != null) {
            Log.i(TAG, "Canceling scans ...");
            alarmMgr.cancel(alarmIntent);
            alarmIntent.cancel();
            alarmIntent = null;
        }


    }

    private void scanAndUpload() {
        ensureBTAndStartScan();
    }

    private void scanLeDevice() {
        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(stopScanAndProcessRunnable, SCAN_PERIOD);

        resetCachedSampleData();
        mBluetoothAdapter.getBluetoothLeScanner().startScan(scanFilters, settings, mScanCallback);
    }

    private void resetCachedSampleData() {
        deviceNr8 = null; // reset samples!
        deviceNr9 = null; // reset samples!
        deviceNr10 = null; // reset samples!
    }

    private boolean hasAllSampleData() {
        return deviceNr8 != null && deviceNr9 != null && deviceNr10 != null;
    }

    private boolean hasAnySampleData() {
        return deviceNr8 != null || deviceNr9 != null || deviceNr10 != null;
    }


    private void stopScanAndProcessResults() {
        mBluetoothAdapter.getBluetoothLeScanner().flushPendingScanResults(mScanCallback);
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
        Log.i(TAG, "Scanner stopped");
        process();
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG, "onScanResult: Result = " + result.toString());
            cacheSample(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                Log.i(TAG, "onBachScanResult: Result = " + result.toString());
                cacheSample(result);
            }
        }

        private void cacheSample(ScanResult result) {
            Date now = new Date();
            String deviceAddress = result.getDevice().getAddress();
            if (DEVICE_NO08_MAC_ADDRESS.equals(deviceAddress)) {
                deviceNr8 = parseNewDevice(result.getScanRecord(), now, DEVICE_NO8_TEMP_SHIFT_DEGREES, DEVICE_NO8_RELHUM_CALIBRATION);
            } else if (DEVICE_NO09_MAC_ADDRESS.equals(deviceAddress)) {
                deviceNr9 = parse(result.getScanRecord(), now);
            } else if (DEVICE_NO10_MAC_ADDRESS.equals(deviceAddress)) {
                deviceNr10 = parseNewDevice(result.getScanRecord(), now, DEVICE_N10_TEMP_SHIFT_DEGREES, DEVICE_N10_RELHUM_CALIBRATION);
            }
            if (hasAllSampleData()) {
                mHandler.removeCallbacks(stopScanAndProcessRunnable);
                stopScanAndProcessResults();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "onScanFailed: Error Code: " + errorCode);
        }
    };

    private void process() {
        long now = System.currentTimeMillis();
        Storage.storeLastScanTime(getBaseContext(), now);

        if (hasAllSampleData()) {
            Storage.storeLastSuccessfulScanTime(getBaseContext(), now);
            Storage.storeIncompleteScans(getBaseContext(), 0); // reset
            upload();
            Intent intent = UploadService.checkThresholds(this, Storage.readAlertingConfig(this)); // could be done only once a day instead of for every scan cycle
            serviceHelper.startForegroundService(this, intent);
        } else {
            handleIncompleteScan();

            if (hasAnySampleData()) {
                upload();
            } else {
                Log.w(TAG, "Did not receive any results from the devices!");
            }
        }

        //restartBT();
    }

    private void upload() {
        Date timestamp = deviceNr8 != null ? deviceNr8.getTimestamp() : (deviceNr9 != null ? deviceNr9.getTimestamp() : deviceNr10.getTimestamp());
        Log.i(TAG, "Processing samples timestamp=" + timestamp + "\n" + deviceNr8 + "\n" + deviceNr9 + "\n" + deviceNr10);
        Intent intent = UploadService.buildStartUploadIntent(this, timestamp, deviceNr8, deviceNr9, deviceNr10);
        serviceHelper.startForegroundService(this, intent);
    }

    private void handleIncompleteScan() {
        long incompleteScans = Storage.readIncompleteScans(getBaseContext());
        incompleteScans++;
        Storage.storeIncompleteScans(getBaseContext(), incompleteScans);
        Log.i(TAG, "Handling incomplete scan result. Incomplete Scans=" + incompleteScans + " of max " + MAX_INOMPLETE_SAMPLING_ATTEMPTS);
        if (incompleteScans == MAX_INOMPLETE_SAMPLING_ATTEMPTS) {
            sendIncompleteScansAlert(incompleteScans);
        }
    }

    private void sendIncompleteScansAlert(long numberOfIncompleteScans) {
        Log.i(TAG, "Sending incomplete scan alert email...");
        BackgroundMail.newBuilder(this)
                .withUsername(BuildConfig.ALERT_EMAIL_FROM)
                .withPassword(BuildConfig.ALERT_EMAIL_PASSWORD)
                .withMailto(BuildConfig.ALERT_EMAIL_TO)
                .withType(BackgroundMail.TYPE_PLAIN)
                .withSubject(String.format("%s Alert: Incomplete scans", getString(R.string.app_name)))
                .withBody(String.format(Locale.getDefault(), "%d incomplete scans in a row!", numberOfIncompleteScans))
                .withProcessVisibility(false)
                .withOnSuccessCallback(new BackgroundMail.OnSuccessCallback() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "Successfully sent Incomplete Scans Alert Email");
                    }
                })
                .withOnFailCallback(new BackgroundMail.OnFailCallback() {
                    @Override
                    public void onFail() {
                        new FirebaseHelper().sendException(ScannerService.this, new Exception("Failed to send Incomplete Scans Alert Email"));
                    }
                })
                .send();
    }


    private void ensureBTAndStartScan() {
        if (mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "ensureBTAndStartScan: BT already ON ...");
            scanLeDevice();
        } else {
            Log.i(TAG, "ensureBTAndStartScan: BT is OFF. Restarting BT ...");
            restartBT(); // wait 10s after to make sure its restarted (dirty hack!)
            Log.i(TAG, "Starting scan in 10s ...");
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanLeDevice();
                }
            }, 10000);
        }

    }

    private void restartBT() {
        Log.i(TAG, "Disabling BT in 1s ...");
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.disable();
            }
        }, 1000);

        Log.i(TAG, "Re-enabling BT in 5s ...");
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                mBluetoothAdapter.enable();

            }
        }, 5000);
    }

    // Old (bigger) devices
    @Nullable
    private Sample parse(ScanRecord record, Date date) {

        byte[] manufacturerSpecData = record.getManufacturerSpecificData().valueAt(0);

        if (manufacturerSpecData == null) {
            Log.w(TAG, "ManufacturerSpecificData is null");
            return null;
        }

        ByteBuffer bytes = ByteBuffer.wrap(manufacturerSpecData).order(ByteOrder.LITTLE_ENDIAN);

        bytes.get();                          // ? flag
        bytes.getShort();                     // temp*10 (lowest)
        short tempCurrent = bytes.getShort(); // temp*10 (current)
        bytes.getShort();                     // temp*10 (highest)
        byte humidity = bytes.get();          // humidity in %
        int battery = Sample.NOT_SET_INT;     // old device does not provide battery level

        return new Sample(date, record.getDeviceName(),
                ((float) tempCurrent) / 10 + DEVICE_NO9_TEMP_SHIFT_DEGREES,
                (int) Math.round(((int) humidity) * DEVICE_NO9_RELHUM_CALIBRATION),
                battery);
    }

    // New (smaller and colored) devices. See app/external/Temperature-Humidity-Data-Logger-Commands-API.pdf for the protocol
    @NonNull
    private Sample parseNewDevice(ScanRecord record, Date date, float tempCalibrationShift,
                                  double relhumCalibrationMultiplier) {
        ScanRecordParser parser = new ScanRecordParser(record.getBytes());
        BMTempHumi bmTempHumi = new BMTempHumi(parser.getManufacturerData(), parser.getScanResponseData());
        return new Sample(date, record.getDeviceName(), (float) bmTempHumi.getCurrentTemperature() + tempCalibrationShift, (int) Math.round(bmTempHumi.getCurrentHumidity() * relhumCalibrationMultiplier), bmTempHumi.getBatteryLevel());
    }

    public static long getNextScheduled(final Context context) {
        AlarmManager.AlarmClockInfo nextAlarmClock = ((AlarmManager) Objects.requireNonNull(context.getSystemService(Context.ALARM_SERVICE))).getNextAlarmClock();
        return nextAlarmClock != null ? nextAlarmClock.getTriggerTime() : -1;
    }


    private String getNotificationText() {
        long nextTriggerTime = ScannerService.getNextScheduled(this);
        DateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        String text;
        if (nextTriggerTime > -1) {
            text = "Next scan: " + df.format(new Date(nextTriggerTime));
        } else {
            text = "Next scan: NONE";
        }
        return text;
    }


}
