package com.home.weatherstation;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Button startSchedulerButton;
    private Button stopSchedulerButton;
    private TextView schedulerStatus;

    private TextView lastScanTime;
    private TextView lastSuccessfulScanTime;
    private TextView lastUploadTime;
    private TextView lastIncompleteScansAlertTime;
    private TextView incompleteScans;
    private TextView humidityThresholdAlertConfig;
    private ImageView humidtyThresholdAlertConfigEdit;
    private TextView avgHumidity;
    private TextView thresholdAlertHumidity;

    private Button scanAndUploadNowButton;

    private Button testUpload; //FIXME

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        schedulerStatus = (TextView) findViewById(R.id.status);
        startSchedulerButton = (Button) findViewById(R.id.start_button);
        startSchedulerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        stopSchedulerButton = (Button) findViewById(R.id.stop_button);
        stopSchedulerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });

        scanAndUploadNowButton = (Button) findViewById(R.id.scan_now_button);
        scanAndUploadNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanAndUploadNow();
            }
        });

        testUpload = (Button) findViewById(R.id.test_upload);
        testUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // FIXME start remove
                Date noww = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(noww);
                float temp = cal.get(Calendar.HOUR_OF_DAY) + ((float) (cal.get(Calendar.MINUTE) / 10));
                int hum = 40 + (cal.get(Calendar.MINUTE) / 10);
                Sample deviceNr8 = new Sample(noww, "DeviceNo8", temp, hum, 8);
                Sample deviceNr9 = new Sample(noww, "DeviceNo9", temp + 1.5f, hum + 2, 9);
                Sample deviceNr10 = new Sample(noww, "DeviceNo10", temp + 2.5f, hum + 4, 10);
                // FIXME end
                UploadService.startUpload(MainActivity.this, noww, deviceNr8, deviceNr9, deviceNr10);
            }
        });

        lastScanTime = (TextView) findViewById(R.id.last_scan_attempt_time);
        lastSuccessfulScanTime = (TextView) findViewById(R.id.last_scan_success_time);
        lastUploadTime = (TextView) findViewById(R.id.last_upload_success_time);
        incompleteScans = (TextView) findViewById(R.id.incomplete_scans);
        lastIncompleteScansAlertTime = (TextView) findViewById(R.id.incomplete_scans_alert_time);

        humidityThresholdAlertConfig = findViewById(R.id.hum_threshold_config);
        humidtyThresholdAlertConfigEdit = findViewById(R.id.edit_hum_threshold_config);
        humidtyThresholdAlertConfigEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEditDialog(MainActivity.this, Storage.readAlertingConfig(MainActivity.this));
            }
        });
        avgHumidity = (TextView) findViewById(R.id.avg_hum);
        thresholdAlertHumidity = (TextView) findViewById(R.id.hum_threshold_alert);

        String version = "??";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        ((TextView) findViewById(R.id.version)).setText(version);

        enableButtons(false);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.mipmap.ic_launcher);

        startActivityForResult(new Intent(this, AuthActivity.class), 2001);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Storage.registerChangeListener(this, this);
        updateViews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Storage.unregisterChangeListener(this, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2001) {
            if (resultCode == RESULT_OK) {
                FirebaseMessaging.getInstance().subscribeToTopic("actions");
                enableButtons(true);
                Toast.makeText(this, "Authentication successful. Ready to upload ...", Toast.LENGTH_LONG).show();
            } else {
                enableButtons(false);
                Toast.makeText(this, "Authentication FAILED. Clear the data of the App and try again ...", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void enableButtons(boolean enabled) {
        startSchedulerButton.setEnabled(enabled);
        stopSchedulerButton.setEnabled(enabled);
        scanAndUploadNowButton.setEnabled(enabled);
    }

    private void scanAndUploadNow() {
        startService(ScannerService.buildScanAndUploadIntent(this));
    }

    private void start() {
        startSchedulerButton.setEnabled(false);
        startService(ScannerService.buildStartSchedulerIntent(this));
        updateStatusScheduler();
    }

    private void stop() {
        stopSchedulerButton.setEnabled(false);
        startService(ScannerService.buildStopSchedulerIntent(this));
        updateStatusScheduler();
    }

    private void updateViews() {
        updateStatusResults();
        updateStatusScheduler();
    }

    private void updateStatusScheduler() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                long nextTriggerTime = ScannerService.getNextScheduled(MainActivity.this);
                DateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                if (nextTriggerTime > -1) {
                    schedulerStatus.setText("Next scan at:\n" + df.format(new Date(nextTriggerTime)));
                    startSchedulerButton.setEnabled(false);
                    stopSchedulerButton.setEnabled(true);
                } else {
                    schedulerStatus.setText("OFF\nNo scan scheduled.");
                    startSchedulerButton.setEnabled(true);
                    stopSchedulerButton.setEnabled(false);
                }
            }
        }, 1500);
    }

    private void updateStatusResults() {
        lastScanTime.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", Storage.readLastScanTime(this)));
        lastSuccessfulScanTime.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", Storage.readLastSuccessfulScanTime(this)));
        lastUploadTime.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", Storage.readLastUploadTime(this)));

        incompleteScans.setText(String.valueOf(Storage.readIncompleteScans(this)));
        long millis = Storage.readLastIncompleteScanAlertTime(this);
        if (millis > -1) {
            lastIncompleteScansAlertTime.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", millis));
        } else {
            lastIncompleteScansAlertTime.setText("-");
        }

        humidityThresholdAlertConfig.setText(String.format("[%s]", Storage.readAlertingConfig(this).toString()));
        avgHumidity.setText(new DecimalFormat("#.##").format(Storage.readAverageHumidity(this)));
        millis = Storage.readThresholdExceededHumidity(this);
        if (millis > -1) {
            thresholdAlertHumidity.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", millis));
        } else {
            thresholdAlertHumidity.setText("-");
        }


    }

    private void showEditDialog(Context c, final AlertingConfig alertingConfig) {
        final EditText editText = new EditText(c);
        editText.setText(alertingConfig.toString());
        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle("Edit threshold config")
                .setMessage("Format: 45,60")
                .setView(editText)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newConfig = String.valueOf(editText.getText());
                        if (newConfig.matches("\\d+,\\d+")) {
                            String[] thresholds = newConfig.split("[,]");
                            alertingConfig.setLowerThresholdHumidity(Float.valueOf(thresholds[0]));
                            alertingConfig.setUpperThresholdHumidity(Float.valueOf(thresholds[1]));
                            Storage.storeAlertingConfig(MainActivity.this, alertingConfig);
                            updateStatusResults();
                        } else {
                            Toast.makeText(MainActivity.this, "Error: Invalid format", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        updateViews();
    }

}
