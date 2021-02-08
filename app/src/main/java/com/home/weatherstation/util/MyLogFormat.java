package com.home.weatherstation.util;

import android.content.Context;

import com.hypertrack.hyperlog.LogFormat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MyLogFormat extends LogFormat {

    private static DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public MyLogFormat(Context context) {
        super(context);
    }

    public String getFormattedLogMessage(String logLevelName, String tag, String message, String timeStamp,
                                         String senderName, String osVersion, String deviceUUID) {
        return LocalDateTime.now().format(fmt) + " | " + osVersion + " | " + "[" + logLevelName + "/" + tag + "]: " + message;
    }

}
