package com.home.weatherstation;

import android.content.Context;

import com.creativityapps.gmailbackgroundlibrary.BackgroundMail;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.PrintWriter;
import java.io.StringWriter;

public class FirebaseHelper {

    private final FirebaseCrashlytics fc;

    public FirebaseHelper() {
        fc = FirebaseCrashlytics.getInstance();
    }

    public void sendException(final Context context, final Throwable t) {
        t.printStackTrace();
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

    private String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
