package com.home.weatherstation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class ServiceHelper {

    private static final String CHANNEL_ID = "my_service_channelid";

    public ServiceHelper() {
        super();
    }

    public void startForegroundService(Context context, Intent intent) {
        context.startForegroundService(intent);
    }

    public PendingIntent getForegroundServicePendingIntent(Context context, Intent intent) {
        return PendingIntent.getForegroundService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public Notification createNotification(Context context, int importance, String text, boolean ongoing) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = createNotificationChannel(notificationManager);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, channelId);
        return notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(importance)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setOngoing(ongoing)
                .build();
    }


    private String createNotificationChannel(NotificationManager notificationManager) {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            Log.d("ServiceHelper", "Creating new NotificationChannel ...");
            String channelName = "Scanner Service";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
            // omitted the LED color
            channel.setImportance(NotificationManager.IMPORTANCE_NONE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
        return CHANNEL_ID;
    }
}
