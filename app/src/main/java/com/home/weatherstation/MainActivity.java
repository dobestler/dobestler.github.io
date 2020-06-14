package com.home.weatherstation;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
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
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import androidx.appcompat.app.AppCompatActivity;


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
    private TextView avgHumidity;
    private TextView thresholdAlertHumidity;

    private Button scanAndUploadNowButton;

    private ServiceHelper serviceHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceHelper = new ServiceHelper();

        schedulerStatus = findViewById(R.id.status);
        startSchedulerButton = findViewById(R.id.start_button);
        startSchedulerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        stopSchedulerButton = findViewById(R.id.stop_button);
        stopSchedulerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });

        scanAndUploadNowButton = findViewById(R.id.scan_now_button);
        scanAndUploadNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanAndUploadNow();
            }
        });

        lastScanTime = findViewById(R.id.last_scan_attempt_time);
        lastSuccessfulScanTime = findViewById(R.id.last_scan_success_time);
        lastUploadTime = findViewById(R.id.last_upload_success_time);
        incompleteScans = findViewById(R.id.incomplete_scans);
        lastIncompleteScansAlertTime = findViewById(R.id.incomplete_scans_alert_time);

        humidityThresholdAlertConfig = findViewById(R.id.hum_threshold_config);
        ImageView humidtyThresholdAlertConfigEdit = findViewById(R.id.edit_hum_threshold_config);
        humidtyThresholdAlertConfigEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEditDialog(MainActivity.this, Storage.readAlertingConfig(MainActivity.this));
            }
        });
        avgHumidity = findViewById(R.id.avg_hum);
        thresholdAlertHumidity = findViewById(R.id.hum_threshold_alert);

        String version = "??";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        ((TextView) findViewById(R.id.version)).setText(version);

        enableButtons(false);

        Objects.requireNonNull(getSupportActionBar()).setDisplayShowHomeEnabled(true);
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
        serviceHelper.startForegroundService(this, ScannerService.buildScanAndUploadIntent(this));
    }

    private void start() {
        startSchedulerButton.setEnabled(false);
        serviceHelper.startForegroundService(this, ScannerService.buildStartSchedulerIntent(this));
        updateStatusScheduler();
    }

    private void stop() {
        stopSchedulerButton.setEnabled(false);
        serviceHelper.startForegroundService(this, ScannerService.buildStopSchedulerIntent(this));
        updateStatusScheduler();
    }

    private void updateViews() {
        updateStatusResults();
        updateStatusScheduler();
    }

    private void updateStatusScheduler() {
        new Handler().postDelayed(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                long nextTriggerTime = ScannerService.getNextScheduled(MainActivity.this);
                DateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
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
