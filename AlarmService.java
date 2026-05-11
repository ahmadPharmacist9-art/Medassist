package com.medremind.pk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;

/**
 * AlarmService — Short-lived foreground service for medicine alarms.
 *
 * CRITICAL FIXES APPLIED:
 * 1. Channel created ONCE, NEVER deleted — prevents race condition
 * 2. Channel has alarm SOUND set (AudioAttributes.USAGE_ALARM) — was silent before
 * 3. startActivity() REMOVED — fullScreenIntent is the only reliable way on Android 10+
 * 4. stopForeground(false) + stopSelf() after 3 seconds — keeps notification visible
 * 5. WakeLock released in onDestroy() NOT in onStartCommand() — CPU stays awake
 *
 * Place at:
 * C:\medremind-pk.html\android\app\src\main\java\com\medremind\pk\AlarmService.java
 */
public class AlarmService extends Service {

    private static final int    ALARM_NOTIF_ID = 7001;
    public  static final String ALARM_CHANNEL  = "med_alarm_channel";
    private static final String TAG            = "AlarmService";

    @Override
    public void onCreate() {
        super.onCreate();
        // Create channel once in onCreate — never delete it
        createAlarmChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Safety — system restarted with null intent
        if (intent == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String medName   = safe(intent.getStringExtra("medName"),      "Medicine");
        String strength  = safe(intent.getStringExtra("medStrength"),  "");
        String schedTime = safe(intent.getStringExtra("schedTime"),    "");
        String instruct  = safe(intent.getStringExtra("instructions"), "");
        String patient   = safe(intent.getStringExtra("patientName"),  "Patient");
        int    notifId   = intent.getIntExtra("notifId", ALARM_NOTIF_ID);

        // ── Build AlarmActivity intent ────────────────────────────────────────
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK          |
            Intent.FLAG_ACTIVITY_CLEAR_TOP         |
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        );
        alarmIntent.putExtra("medName",      medName);
        alarmIntent.putExtra("medStrength",  strength);
        alarmIntent.putExtra("schedTime",    schedTime);
        alarmIntent.putExtra("instructions", instruct);
        alarmIntent.putExtra("patientName",  patient);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0);

        // Use Intent flag for showing on lock screen (not PendingIntent flag)
        alarmIntent.addFlags(Intent.FLAG_SHOW_WHEN_LOCKED);

        // fullScreenIntent — the ONLY reliable way to show activity on lock screen
        PendingIntent fullScreenPI = PendingIntent.getActivity(
            this, notifId, alarmIntent, piFlags);

        // contentIntent — opens AlarmActivity when user taps notification
        PendingIntent contentPI = PendingIntent.getActivity(
            this, notifId + 1, alarmIntent, piFlags);

        // ── Build notification ────────────────────────────────────────────────
        String title = "💊 " + medName + (strength.isEmpty() ? "" : "  " + strength);
        String body  = patient + " — ⏰ " + schedTime +
                       (instruct.isEmpty() ? "" : "\n⚠️ " + instruct);

// Get alarm sound for notification builder (for all Android versions)
        Uri alarmSoundBuilder = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSoundBuilder == null) {
            alarmSoundBuilder = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, ALARM_CHANNEL)           // own dedicated channel
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(patient + " — " + schedTime)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPI, true) // shows AlarmActivity on lock screen
            .setContentIntent(contentPI)
            .setOngoing(true)
            .setAutoCancel(false);
            // FIX: Set sound on builder too (works on all Android versions)
            if (alarmSoundBuilder != null) {
                builder.setSound(alarmSoundBuilder);
            }

        Notification notification = builder.build();

        // ── CRITICAL: startForeground FIRST ──────────────────────────────────
        startForeground(notifId, notification);

        // ── DO NOT call startActivity() ───────────────────────────────────────
        // On Android 10+ startActivity() from background service silently fails.
        // fullScreenIntent on the notification handles showing AlarmActivity.

        // ── Stop service after 3 seconds but KEEP notification visible ────────
        // stopForeground(false) = stop service foreground state but keep notification
        // This prevents Android 14 from killing it as a "shortService" violation
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                stopForeground(false); // false = KEEP notification
                Log.d(TAG, "stopForeground called — notification remains visible");
            } catch (Exception e) {
                Log.e(TAG, "stopForeground error: " + e.getMessage());
            }
            try {
                stopSelf();
                Log.d(TAG, "stopSelf called");
            } catch (Exception e) {
                Log.e(TAG, "stopSelf error: " + e.getMessage());
            }
        }, 3000); // 3 second delay

        return START_NOT_STICKY;
    }

    private void createAlarmChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // NEVER delete the channel — creates race condition
        // Only create if it doesn't already exist
        if (nm.getNotificationChannel(ALARM_CHANNEL) != null) return;

        NotificationChannel channel = new NotificationChannel(
            ALARM_CHANNEL,
            "Medicine Alarms",
            NotificationManager.IMPORTANCE_HIGH    // MUST be HIGH
        );
        channel.setDescription("Critical medicine dose alarms");
        channel.setBypassDnd(true);                // bypass Do Not Disturb
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 700, 300, 700});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        // ── CRITICAL FIX: Set alarm sound on channel ──────────────────────────
        // On Android 8+, sound is controlled by the CHANNEL not the notification builder.
        // Without this, the alarm is completely silent.
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM) // USAGE_ALARM = plays on alarm stream
            .build();
        channel.setSound(alarmSound, audioAttributes);

        nm.createNotificationChannel(channel);
        Log.d(TAG, "Alarm channel created with sound: " + alarmSound);
    }

    @Override
    public void onDestroy() {
        // ── Release WakeLock HERE when service actually dies ──────────────────
        // Releasing in onStartCommand was too early — CPU could sleep before
        // the notification was fully processed by the system.
        try {
            if (AlarmReceiver.wakeLock != null && AlarmReceiver.wakeLock.isHeld()) {
                AlarmReceiver.wakeLock.release();
                Log.d(TAG, "WakeLock released in onDestroy");
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private static String safe(String s, String fb) {
        return (s != null && !s.isEmpty()) ? s : fb;
    }
}
