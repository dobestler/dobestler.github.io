package com.home.weatherstation;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Button startSchedulerButton;
    private Button stopSchedulerButton;
    private TextView schedulerStatus;

    private TextView lastScanTime;
    private TextView lastSuccessfulScanTime;
    private TextView lastUploadTime;

    private Button scanAndUploadNowButton;


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
            public void onClick(View view) {
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

        lastScanTime = (TextView) findViewById(R.id.last_scan_attempt_time);
        lastSuccessfulScanTime = (TextView) findViewById(R.id.last_scan_success_time);
        lastUploadTime = (TextView) findViewById(R.id.last_upload_success_time);

        enableButtons(false);

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
        startService(ScannerService.buildStartSchedulerIntent(this));
        updateStatusScheduler();
    }

    private void stop() {
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
                schedulerStatus.setText(nextTriggerTime > -1 ? "Next scan at:\n" + new Date(nextTriggerTime).toString() : "OFF\nNo scan scheduled.");
            }
        }, 1000);
    }

    private void updateStatusResults() {
        lastScanTime.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", Storage.readLastScanTime(this)));
        lastSuccessfulScanTime.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", Storage.readLastSuccessfulScanTime(this)));
        lastUploadTime.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", Storage.readLastUploadTime(this)));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        updateViews();
    }
}
