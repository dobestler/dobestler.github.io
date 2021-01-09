package com.home.weatherstation;

import android.app.Application;

import com.home.weatherstation.util.MyLog;
import com.home.weatherstation.util.MyLogFormat;
import com.hypertrack.hyperlog.HyperLog;

public class MyApplication extends Application {


    private static String TAG = MyApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        HyperLog.initialize(getApplicationContext(), new MyLogFormat(this));
        HyperLog.setLogLevel(MyLog.MIN_LOG_LEVEL);
        MyLog.i(TAG, "Application onCreate");
    }
}
