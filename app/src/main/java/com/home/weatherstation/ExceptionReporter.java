package com.home.weatherstation;

import android.content.Context;

import com.creativityapps.gmailbackgroundlibrary.BackgroundMail;
import com.home.weatherstation.util.MyLog;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.Locale;

public class ExceptionReporter {
    private static final String TAG = ExceptionReporter.class.getSimpleName();

//    private final FirebaseCrashlytics fc;

    public ExceptionReporter() {
//        fc = FirebaseCrashlytics.getInstance();
    }

    public void sendException(final Context context, final Throwable t) {
        sendException(context, t, "", "");
    }

    public void sendException(final Context context, final Throwable t, final String logTag, final String logMessage) {
        MyLog.e(logTag, logMessage, t);

        // Since the following uploads the exceptions only on app-relaunch, we simply send an email instead
//        fc.recordException(t);
//        fc.sendUnsentReports();

        BackgroundMail.newBuilder(context)
                .withUsername(BuildConfig.ALERT_EMAIL_FROM)
                .withPassword(BuildConfig.ALERT_EMAIL_PASSWORD)
                .withMailto(BuildConfig.ALERT_EMAIL_TO)
                .withType(BackgroundMail.TYPE_PLAIN)
                .withSubject("Non-Fatal Exception")
                .withBody(stackTraceToString(t))
                .withProcessVisibility(false)
                .send();
    }

    public void sendIncompleteScansAlert(final Context context, long numberOfIncompleteScans, Sample deviceNr8, Sample deviceNr9, Sample deviceNr10) {
        MyLog.i(TAG, "Sending incomplete scan alert email...");
        String body1 = String.format(Locale.getDefault(), "%d incomplete scans in a row!", numberOfIncompleteScans);
        String body2 = deviceNr8 + "\n" + deviceNr9 + "\n" + deviceNr10;
        BackgroundMail.newBuilder(context)
                .withUsername(BuildConfig.ALERT_EMAIL_FROM)
                .withPassword(BuildConfig.ALERT_EMAIL_PASSWORD)
                .withMailto(BuildConfig.ALERT_EMAIL_TO)
                .withType(BackgroundMail.TYPE_PLAIN)
                .withSubject(String.format("%s Alert: Incomplete scans", context.getString(R.string.app_name)))
                .withBody(body1 + "\n" + body2)
                .withProcessVisibility(false)
                .send();
    }

    public void sendThresholdRecoveredAlert(final Context context, double recoveringValue,
                                            int lastXdays, float lowerThreshold, float upperThreshold) {
        MyLog.i(TAG, "Sending Threshold Recovered alert email...");
        String subject = String.format("%s Alert: %s threshold recovered", context.getString(R.string.app_name), "Humidity");
        String body = String.format(Locale.getDefault(), "Measured avg. for the last %d days = %s \n" +
                "Lower threshold = %s\n" +
                "Upper threshold = %s", lastXdays, new DecimalFormat("#.##").format(recoveringValue), new DecimalFormat("#.##").format(lowerThreshold), new DecimalFormat("#.##").format(upperThreshold));
        sendEmail(context, subject, body);
    }

    public void sendThresholdExceededAlert(final Context context, double exceedingValue,
                                           int lastXdays, float lowerThreshold, float upperThreshold) {
        MyLog.i(TAG, "Sending Threshold Exceeded alert email...");
        String subject = String.format("%s Alert: %s threshold exceeded", context.getString(R.string.app_name), "Humidity");
        String body = String.format(Locale.getDefault(), "Measured avg. for the last %d days = %s \n" +
                "Lower threshold = %s\n" +
                "Upper threshold = %s", lastXdays, new DecimalFormat("#.##").format(exceedingValue), new DecimalFormat("#.##").format(lowerThreshold), new DecimalFormat("#.##").format(upperThreshold));
        sendEmail(context, subject, body);
    }

    private void sendEmail(Context context, String subject, String body) {
        BackgroundMail.newBuilder(context)
                .withUsername(BuildConfig.ALERT_EMAIL_FROM)
                .withPassword(BuildConfig.ALERT_EMAIL_PASSWORD)
                .withMailto(BuildConfig.ALERT_EMAIL_TO)
                .withType(BackgroundMail.TYPE_PLAIN)
                .withSubject(subject)
                .withBody(body)
                .withProcessVisibility(false)
                .send();
    }

    private String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
