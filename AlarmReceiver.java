package com.medremind.pk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import androidx.core.content.ContextCompat;

/**
 * AlarmReceiver — receives AlarmManager broadcast at exact medicine time.
 *
 * CRITICAL FIXES:
 * 1. Acquires WakeLock IMMEDIATELY to keep CPU awake
 * 2. WakeLock is NOT released here — released in AlarmService.onDestroy()
 *    so CPU stays awake until notification is fully displayed
 *
 * Place at:
 * C:\medremind-pk.html\android\app\src\main\java\com\medremind\pk\AlarmReceiver.java
 */
public class AlarmReceiver extends BroadcastReceiver {

    // Static so AlarmService can release it in onDestroy()
    public static PowerManager.WakeLock wakeLock;

    @Override
    public void onReceive(Context context, Intent intent) {

        // ── Step 1: Acquire WakeLock IMMEDIATELY ─────────────────────────────
        // This keeps the CPU awake long enough for AlarmService to show the UI.
        // DO NOT release here — AlarmService.onDestroy() releases it.
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.ON_AFTER_RELEASE,
                "MedRemind::AlarmWakeLock"
            );
            wakeLock.acquire(2 * 60 * 1000L); // hold for 2 minutes max
        }

        // ── Step 2: Start AlarmService as foreground ──────────────────────────
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtras(intent); // forward all medicine details
        ContextCompat.startForegroundService(context, serviceIntent);
    }
}
