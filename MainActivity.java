package com.medremind.pk;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import com.medremind.pk.MedReminderService;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import org.json.JSONArray;

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

        // Start persistent service to keep app alive for alarms
        try {
            Intent svc = new Intent(this, MedReminderService.class);
            svc.setAction(MedReminderService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception ignored) {}

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

        // Handle boot reschedule intent (from BootReceiver)
        Intent intent = getIntent();
        boolean shouldReschedule = intent != null && 
            intent.getBooleanExtra("reschedule_alarms", false);
        if (shouldReschedule) {
            rescheduleStoredAlarms();
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

    // Reschedule alarms from persistent storage after boot
    private void rescheduleStoredAlarms() {
        try {
            // Load and reschedule saved alarms
            org.json.JSONArray alarms = AlarmStorage.loadAlarms(this);
            if (alarms.length() == 0) return;
            
            android.app.AlarmManager am = (android.app.AlarmManager) 
                getSystemService(ALARM_SERVICE);
            if (am == null) return;

            for (int i = 0; i < alarms.length(); i++) {
                try {
                    org.json.JSONObject a = alarms.getJSONObject(i);
                    int alarmId = a.getInt("alarmId");
                    String medName = a.optString("medName", "Medicine");
                    String strength = a.optString("medStrength", "");
                    String schedTime = a.optString("schedTime", "");
                    String instruct = a.optString("instructions", "");
                    String patient = a.optString("patientName", "Patient");
                    int hour = a.optInt("hour", 0);
                    int minute = a.optInt("minute", 0);
                    int dayOff = a.optInt("dayOffset", 0);

                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.add(java.util.Calendar.DAY_OF_YEAR, dayOff);
                    cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
                    cal.set(java.util.Calendar.MINUTE, minute);
                    cal.set(java.util.Calendar.SECOND, 0);
                    cal.set(java.util.Calendar.MILLISECOND, 0);

                    // Skip past times
                    if (cal.getTimeInMillis() < System.currentTimeMillis() - 60_000L)
                        continue;

                    Intent alarmIntent = new Intent(this, AlarmReceiver.class);
                    alarmIntent.setAction("MEDICINE_ALARM_" + alarmId);
                    alarmIntent.putExtra("medName", medName);
                    alarmIntent.putExtra("medStrength", strength);
                    alarmIntent.putExtra("schedTime", schedTime);
                    alarmIntent.putExtra("instructions", instruct);
                    alarmIntent.putExtra("patientName", patient);
                    alarmIntent.putExtra("notifId", alarmId);

                    int piFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_IMMUTABLE : 0);

                    PendingIntent pi = PendingIntent.getBroadcast(
                        this, alarmId, alarmIntent, piFlags);

                    // Use setAlarmClock for Android 14+
                    AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(
                        cal.getTimeInMillis(), pi);
                    am.setAlarmClock(clockInfo, pi);
                } catch (Exception ignored) {}
            }
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
