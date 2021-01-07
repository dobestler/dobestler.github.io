package com.home.weatherstation;

import android.app.Application;
import android.util.Log;

import com.home.weatherstation.util.MyLogFormat;
import com.hypertrack.hyperlog.HyperLog;

public class MyApplication extends Application {

    private static String TAG = MyApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        HyperLog.initialize(getApplicationContext(), new MyLogFormat(this));
        HyperLog.setLogLevel(Log.DEBUG);
        HyperLog.i(TAG, "Application onCreate");
    }
}
