package com.okode.richlocalnotifications;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.LogUtils;
import com.getcapacitor.PluginCall;
import com.getcapacitor.plugin.notification.TimedNotificationPublisher;
import com.okode.richlocalnotifications.capacitorrichlocalnotifications.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class RichLocalNotificationManager {

    private static final String NOTIFICATION_INTENT_KEY = "RichLocalNotificationId";
    private static final String NOTIFICATION_OBJ_INTENT_KEY = "RichLocalNotficationObject";
    private static final String ACTION_INTENT_KEY = "RichLocalNotificationUserAction";
    private static final String DEFAULT_PRESS_ACTION = "tap";

    private Context context;
    private Class<?> mainActivityClazz;

    public RichLocalNotificationManager(Context context) {
        this.context = context;
        this.mainActivityClazz = this.context.getClass();
        createDefaultNotificationChannel();
    }

    public RichLocalNotificationManager(Context context, Class<?> mainActivityClazz) {
        this.context = context;
        this.mainActivityClazz = mainActivityClazz;
        createDefaultNotificationChannel();
    }

    /**
     * Method extecuted when notification is launched by user from the notification bar.
     */
    public JSObject handleNotificationActionPerformed(Intent data) {
        Log.d(LogUtils.getPluginTag("RLN"), "RichLocalNotification received: " + data.getDataString());
        int notificationId = data.getIntExtra(NOTIFICATION_INTENT_KEY, Integer.MIN_VALUE);
        if (notificationId == Integer.MIN_VALUE) {
            Log.d(LogUtils.getPluginTag("RLN"), "Activity started without notification attached");
            return null;
        }
        JSObject dataJson = new JSObject();

        String menuAction = data.getStringExtra(ACTION_INTENT_KEY);
        if (menuAction != DEFAULT_PRESS_ACTION) {
            dismissVisibleNotification(notificationId);
        }
        dataJson.put("actionId", menuAction);
        JSONObject request = null;
        try {
            String notificationJsonString = data.getStringExtra(NOTIFICATION_OBJ_INTENT_KEY);
            if (notificationJsonString != null) {
                request = new JSObject(notificationJsonString);
            }
        } catch (JSONException e) {
            Log.e(LogUtils.getPluginTag("RLN"), "Error getting notification data", e);
        }
        dataJson.put("notification", request);
        return dataJson;
    }

    public Integer show(PluginCall call, RichLocalNotification richLocalNotification) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        boolean notificationsEnabled = notificationManager.areNotificationsEnabled();
        if (!notificationsEnabled) {
            call.error("Notifications not enabled on this device");
            return null;
        }
        Integer id = richLocalNotification.getId();
        if (richLocalNotification.getId() == null) {
            call.error("RichLocalNotification missing identifier");
            return null;
        }
        dismissVisibleNotification(id);
        return postNotification(richLocalNotification);
    }

    public Integer postNotification(RichLocalNotification richLocalNotification) {
        if (richLocalNotification == null) { return null; }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder mBuilder = getNotificationBuilder(richLocalNotification);
        createActionIntents(richLocalNotification, mBuilder);
        Notification buildNotification = mBuilder.build();
        if (richLocalNotification.isScheduled()) {
            triggerScheduledNotification(buildNotification, richLocalNotification);
        } else {
            notificationManager.notify(richLocalNotification.getId(), buildNotification);
        }
        return richLocalNotification.getId();
    }

    protected void createDefaultNotificationChannel() {
        createHighPriorityNotificationChannel();
    }

    protected String getDefaultChannelId() {
        return context.getString(R.string.rich_local_notifications_default_channel_id);
    }

    protected String getDefaultChannelName() {
        return context.getString(R.string.rich_local_notifications_default_channel_name);
    }

    protected NotificationCompat.Builder getNotificationBuilder(RichLocalNotification richLocalNotification) {
        String channelId = richLocalNotification.getChannelId() != null ?
                richLocalNotification.getChannelId() : getDefaultChannelId();
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(richLocalNotification.getTitle())
                .setContentText(richLocalNotification.getBody())
                .setAutoCancel(true)
                .setOngoing(false)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);

        if (richLocalNotification.getPriority() != null) {
            mBuilder.setPriority(richLocalNotification.getPriority());
        } else {
            mBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
        }

        String sound = richLocalNotification.getSound();
        if (sound != null) {
            Uri soundUri = Uri.parse(sound);
            // Grant permission to use sound
            context.grantUriPermission(
                    "com.android.systemui", soundUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            mBuilder.setSound(soundUri);
        }

        mBuilder.setVisibility(Notification.VISIBILITY_PRIVATE);
        mBuilder.setOnlyAlertOnce(true);

        mBuilder.setSmallIcon(richLocalNotification.getSmallIconId());
        return mBuilder;
    }

    protected String getOpenAction() {
        return DEFAULT_PRESS_ACTION;
    }

    // Create intent for open action
    protected void createActionIntents(RichLocalNotification richLocalNotification, NotificationCompat.Builder mBuilder) {
        // Open intent
        Intent intent = buildIntent(richLocalNotification, getOpenAction());
        PendingIntent pendingIntent = PendingIntent.getActivity(context, richLocalNotification.getId(), intent, PendingIntent.FLAG_CANCEL_CURRENT);
        mBuilder.setContentIntent(pendingIntent);
    }

    protected Intent buildIntent(RichLocalNotification richLocalNotification, String action) {
        Intent intent = new Intent(context, mainActivityClazz);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(NOTIFICATION_INTENT_KEY, richLocalNotification.getId());
        intent.putExtra(ACTION_INTENT_KEY, action);
        intent.putExtra(NOTIFICATION_OBJ_INTENT_KEY, richLocalNotification.getSource());
        return intent;
    }

    private void createHighPriorityNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // It seems an Android bug because IMPORTANCE_MAX is an allowed constant
            @SuppressLint("WrongConstant") NotificationChannel channel = new NotificationChannel(
                    getDefaultChannelId(),
                    getDefaultChannelName(), NotificationManager.IMPORTANCE_MAX);
            channel.setShowBadge(true);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            android.app.NotificationManager notificationManager =
                    context.getSystemService(android.app.NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Build a notification trigger, such as triggering each N seconds, or
     * on a certain date "shape" (such as every first of the month)
     */
    protected void triggerScheduledNotification(Notification notification, RichLocalNotification richLocalNotification) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        RichLocalNotificationSchedule schedule = richLocalNotification.getSchedule();
        Intent notificationIntent = new Intent(context, TimedNotificationPublisher.class);
        notificationIntent.putExtra(NOTIFICATION_INTENT_KEY, richLocalNotification.getId());
        notificationIntent.putExtra(TimedNotificationPublisher.NOTIFICATION_KEY, notification);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, richLocalNotification.getId(), notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Schedule at specific time (with repeating support)
        Date at = schedule.getAt();
        if (at != null) {
            if (at.getTime() < new Date().getTime()) {
                Log.e(LogUtils.getPluginTag("RLN"), "Scheduled time must be *after* current time");
                return;
            }
            if (schedule.isRepeating()) {
                long interval = at.getTime() - new Date().getTime();
                alarmManager.setRepeating(AlarmManager.RTC, at.getTime(), interval, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC, at.getTime(), pendingIntent);
            }
            return;
        }

        // Schedule at specific intervals
        String every = schedule.getEvery();
        if (every != null) {
            Long everyInterval = schedule.getEveryInterval();
            if (everyInterval != null) {
                long startTime = new Date().getTime() + everyInterval;
                alarmManager.setRepeating(AlarmManager.RTC, startTime, everyInterval, pendingIntent);
            }
            return;
        }

    }


    private void dismissVisibleNotification(int notificationId) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(notificationId);
    }

}
