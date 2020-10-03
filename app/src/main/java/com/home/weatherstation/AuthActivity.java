package com.home.weatherstation;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;

import java.io.IOException;
import java.util.Arrays;

public class AuthActivity extends Activity {

    public static final int RESULT_FAILED = 99;

    private static final String TAG = AuthActivity.class.getSimpleName();

    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final int REQUEST_ACCOUNT_PICKER = 1000;
    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;

    private AuthPreferences authPreferences;
    private AndroidPermissions mPermissions;
    private GoogleAccountCredential credential;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        credential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(UploadService.SCOPES))
                .setBackOff(new ExponentialBackOff());

        authPreferences = new AuthPreferences(this);

        // TODO API 30 Background Location https://developer.android.com/about/versions/11/privacy/location
        // https://developer.android.com/about/versions/11/privacy/foreground-services
        mPermissions = new AndroidPermissions(this,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.GET_ACCOUNTS);

        ensurePreconditions();
    }

    private void ensurePreconditions() {
        if (mPermissions.checkPermissions()) {
            doStart();
        } else {
            Log.d(TAG, "Some needed permissions are missing. Requesting them.");
            mPermissions.requestPermissions(PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.d(TAG, "onRequestPermissionsResult");

        if (mPermissions.areAllRequiredPermissionsGranted(permissions, grantResults)) {
            doStart();
        } else {
            showError("Can not start: Insufficient Permissions");
        }
    }

    private void doStart() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (authPreferences.getUser() == null) {
            startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
        } else {
            credential.setSelectedAccountName(authPreferences.getUser());
            new MakeRequestTask(credential).execute();
        }
    }

    private void showError(String message) {
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void returnFailedAndFinish() {
        setResult(RESULT_FAILED);
        finish();
    }

    private void returnAndFinish() {
        setResult(RESULT_OK);
        finish();
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                AuthActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    showError("This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.");
                } else {
                    doStart();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        authPreferences.setUser(accountName);
                        doStart();
                    }
                } else {
                    showError("This app requires a valid account for authorization.");
                }
                break;

        }
    }

    /**
     * Background task to test authentication
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, String> {
        private final GoogleAccountCredential credential;

        MakeRequestTask(GoogleAccountCredential credential) {
            this.credential = credential;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                String oauthToken = credential.getToken();
                Log.d(TAG, "Acquired token " + oauthToken);
                return oauthToken;
            } catch (UserRecoverableAuthException e) {
                // Requesting an authorization code will always throw
                // UserRecoverableAuthException on the first call to GoogleAuthUtil.getToken
                // because the user must consent to offline access to their data.  After
                // consent is granted control is returned to your activity in onActivityResult
                // and the second call to GoogleAuthUtil.getToken will succeed.
                startActivityForResult(e.getIntent(), REQUEST_ACCOUNT_PICKER);
                return null;
            } catch (GoogleAuthException |
                    IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            //
        }

        @Override
        protected void onPostExecute(String output) {
            if (output == null) {
                returnFailedAndFinish();
            } else {
                returnAndFinish();
            }
        }

        @Override
        protected void onCancelled() {
            showError("Async authentication cancelled!");
        }
    }

}