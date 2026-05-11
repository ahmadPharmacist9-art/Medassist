package com.medremind.pk;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

/**
 * MainActivity for MedRemind PK
 *
 * Place at:
 * C:\medremind-pk.html\android\app\src\main\java\com\medremind\pk\MainActivity.java
 *
 * REPLACES your existing MainActivity.java completely.
 */
public class MainActivity extends BridgeActivity {

    public static final String CHANNEL_ID           = "medremind_doses";
    public static final String CHANNEL_ID_EMERGENCY = "medremind_emergency";
    private static final int   NOTIF_PERM_CODE      = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(MedRemindPlugin.class);
        super.onCreate(savedInstanceState);

        createChannels();

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIF_PERM_CODE);
            }
        }

        // Request exact alarm permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager am =
                (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent i = new Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        }

        // Start background monitoring service
        try {
            Intent svc = new Intent(this, MedReminderService.class);
            svc.setAction(MedReminderService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(svc);
            else
                startService(svc);
        } catch (Exception ignored) {}
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // Medicine alarms — IMPORTANCE_HIGH = heads-up banner + sound
        NotificationChannel doses = new NotificationChannel(
            CHANNEL_ID,
            "Medicine Reminders",
            NotificationManager.IMPORTANCE_HIGH
        );
        doses.setDescription("Alerts when it is time to take a medicine");
        doses.enableVibration(true);
        doses.setShowBadge(true);
        doses.setBypassDnd(true);
        doses.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(doses);

        // Emergency / service — quiet persistent
        NotificationChannel emergency = new NotificationChannel(
            CHANNEL_ID_EMERGENCY,
            "Emergency & Service",
            NotificationManager.IMPORTANCE_DEFAULT
        );
        emergency.setDescription("Emergency contact and background service");
        emergency.setShowBadge(false);
        emergency.enableVibration(false);
        emergency.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(emergency);
    }
}
