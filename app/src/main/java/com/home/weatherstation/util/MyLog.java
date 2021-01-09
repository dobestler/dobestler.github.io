package com.home.weatherstation.util;

import android.util.Log;

import com.hypertrack.hyperlog.HyperLog;

/**
 * HACK
 * This class only exists to ensure the minimum log level because HyperLog sends all logs and even
 * sets the wrong level: See how @{@link HyperLog#v(String, String)} properly logs but then stores
 * the initialized static loglevel to the database => r(getFormattedLog(logLevel, tag, message));
 * instead of the respective log level of the called method, i.e. for v(...) it should be
 * r(getFormattedLog(Log.VERBOSE, tag, message));
 */
public class MyLog {

    public static int MIN_LOG_LEVEL = Log.DEBUG;

    public static void v(String tag, String message) {
        v(tag, message, null);
    }

    public static void v(String tag, String message, Throwable tr) {
        if (Log.VERBOSE >= MIN_LOG_LEVEL) {
            HyperLog.v(tag, message, tr);
        }
    }

    public static void d(String tag, String message) {
        d(tag, message, null);
    }

    public static void d(String tag, String message, Throwable tr) {
        if (Log.DEBUG >= MIN_LOG_LEVEL) {
            HyperLog.d(tag, message, tr);
        }
    }

    public static void i(String tag, String message) {
        i(tag, message, null);
    }

    public static void i(String tag, String message, Throwable tr) {
        if (Log.INFO >= MIN_LOG_LEVEL) {
            HyperLog.i(tag, message, tr);
        }
    }

    public static void w(String tag, String message) {
        w(tag, message, null);
    }

    public static void w(String tag, String message, Throwable tr) {
        if (Log.WARN >= MIN_LOG_LEVEL) {
            HyperLog.w(tag, message, tr);
        }
    }

    public static void e(String tag, String message) {
        e(tag, message, null);
    }

    public static void e(String tag, String message, Throwable tr) {
        if (Log.ERROR >= MIN_LOG_LEVEL) {
            HyperLog.e(tag, message, tr);
        }
    }
}
