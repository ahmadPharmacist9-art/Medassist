package com.medremind.pk;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

/**
 * MedReminderService — Persistent background service
 *
 * Keeps the app process alive so AlarmManager alarms
 * can fire reliably. Shows a minimal persistent notification.
 *
 * Place at:
 * C:\medremind-pk.html\android\app\src\main\java\com\medremind\pk\MedReminderService.java
 */
public class MedReminderService extends Service {

    public static final String ACTION_START = "START_SERVICE";
    public static final String ACTION_STOP  = "STOP_SERVICE";
    private static final int   NOTIF_ID     = 5001;

    private Handler  handler;
    private Runnable keepAlive;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null && intent.getAction() != null)
            ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, buildNotification());
        startKeepAlive();
        return START_STICKY; // Android restarts if killed
    }

    private Notification buildNotification() {
        Intent openApp = getPackageManager()
            .getLaunchIntentForPackage(getPackageName());
        if (openApp == null) openApp = new Intent(this, MainActivity.class);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0);

        PendingIntent pi = PendingIntent.getActivity(this, NOTIF_ID, openApp, piFlags);

        return new NotificationCompat.Builder(this, MainActivity.CHANNEL_ID_EMERGENCY)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("MedRemind PK")
            .setContentText("Medicine reminders are active")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    private void startKeepAlive() {
        if (keepAlive != null) handler.removeCallbacks(keepAlive);
        keepAlive = new Runnable() {
            @Override public void run() {
                // Just keep process alive — AlarmManager handles the rest
                handler.postDelayed(this, 30_000);
            }
        };
        handler.post(keepAlive);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && keepAlive != null)
            handler.removeCallbacks(keepAlive);
        // Self-restart
        Intent restart = new Intent(this, MedReminderService.class);
        restart.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(restart);
        else
            startService(restart);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
