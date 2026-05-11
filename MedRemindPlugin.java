package com.medremind.pk;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONObject;
import java.util.Calendar;

/**
 * MedRemindPlugin — Custom Capacitor bridge
 *
 * Place at:
 * C:\medremind-pk.html\android\app\src\main\java\com\medremind\pk\MedRemindPlugin.java
 */
@CapacitorPlugin(name = "MedRemind")
public class MedRemindPlugin extends Plugin {

    private static final int EMERGENCY_NOTIF_ID = 8002;

    // ── Battery optimization exemption ────────────────────────────────────────
    @PluginMethod
    public void requestBatteryOptimizationExemption(PluginCall call) {
        Context ctx = getContext();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) {
                    Intent intent = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + ctx.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(intent);
                }
            }
            call.resolve();
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
            call.resolve();
        }
    }

    // ── Start background service ──────────────────────────────────────────────
    @PluginMethod
    public void startBackgroundService(PluginCall call) {
        try {
            Context ctx = getContext();
            Intent svc = new Intent(ctx, MedReminderService.class);
            svc.setAction(MedReminderService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(svc);
            else
                ctx.startService(svc);
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    // ── Stop background service ───────────────────────────────────────────────
    @PluginMethod
    public void stopBackgroundService(PluginCall call) {
        try {
            Context ctx = getContext();
            Intent svc = new Intent(ctx, MedReminderService.class);
            svc.setAction(MedReminderService.ACTION_STOP);
            ctx.startService(svc);
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    // ── Schedule AlarmManager alarms ─────────────────────────────────────────
    @PluginMethod
    public void scheduleMedicineAlarms(PluginCall call) {
        try {
            Context ctx = getContext();
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) { call.reject("No AlarmManager"); return; }

            JSArray alarms = call.getArray("alarms");
            if (alarms == null) {
                call.resolve(new JSObject().put("scheduled", 0));
                return;
            }

            int scheduled = 0;
            for (int i = 0; i < alarms.length(); i++) {
                try {
                    JSONObject a    = alarms.getJSONObject(i);
                    int    alarmId  = a.getInt("alarmId");
                    String medName  = a.optString("medName",     "Medicine");
                    String strength = a.optString("medStrength", "");
                    String schedTime= a.optString("schedTime",   "");
                    String instruct = a.optString("instructions","");
                    String patient  = a.optString("patientName", "Patient");
                    int    hour     = a.getInt("hour");
                    int    minute   = a.getInt("minute");
                    int    dayOff   = a.optInt("dayOffset", 0);

                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_YEAR, dayOff);
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE,      minute);
                    cal.set(Calendar.SECOND,      0);
                    cal.set(Calendar.MILLISECOND, 0);

                    // Skip past times (with 60s buffer)
                    if (cal.getTimeInMillis() < System.currentTimeMillis() - 60_000L)
                        continue;

                    // Build broadcast intent → AlarmReceiver → AlarmService → AlarmActivity
                    Intent intent = new Intent(ctx, AlarmReceiver.class);
                    intent.setAction("MEDICINE_ALARM_" + alarmId);
                    intent.putExtra("medName",      medName);
                    intent.putExtra("medStrength",  strength);
                    intent.putExtra("schedTime",    schedTime);
                    intent.putExtra("instructions", instruct);
                    intent.putExtra("patientName",  patient);
                    intent.putExtra("notifId",      alarmId);

                    int piFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_IMMUTABLE : 0);

                    PendingIntent pi = PendingIntent.getBroadcast(
                        ctx, alarmId, intent, piFlags);

                    // setExactAndAllowWhileIdle fires even in Doze mode
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                    } else {
                        am.setExact(
                            AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                    }
                    scheduled++;

                } catch (Exception ex) { /* skip bad entry */ }
            }

            JSObject result = new JSObject();
            result.put("scheduled", scheduled);
            call.resolve(result);

        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    // ── Cancel a specific alarm ───────────────────────────────────────────────
    @PluginMethod
    public void cancelMedicineAlarm(PluginCall call) {
        try {
            Context ctx     = getContext();
            int     alarmId = call.getInt("alarmId", -1);
            if (alarmId == -1) { call.resolve(); return; }

            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ctx, AlarmReceiver.class);
            intent.setAction("MEDICINE_ALARM_" + alarmId);

            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? PendingIntent.FLAG_IMMUTABLE : 0);

            PendingIntent pi = PendingIntent.getBroadcast(ctx, alarmId, intent, piFlags);
            if (am != null) am.cancel(pi);
            pi.cancel();
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    // ── Persistent emergency contact on lock screen ───────────────────────────
    @PluginMethod
    public void showEmergencyLockScreen(PluginCall call) {
        String patientName = safe(call.getString("patientName"), "Patient");
        String ecName      = safe(call.getString("ecName"),      "Emergency Contact");
        String ecNum       = safe(call.getString("ecNum"),       "");
        String allergies   = safe(call.getString("allergies"),   "");

        if (ecNum.isEmpty()) { call.resolve(); return; }

        Context ctx = getContext();
        Intent openApp = ctx.getPackageManager()
            .getLaunchIntentForPackage(ctx.getPackageName());
        if (openApp == null) openApp = new Intent(ctx, MainActivity.class);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0);

        PendingIntent openPI = PendingIntent.getActivity(
            ctx, EMERGENCY_NOTIF_ID, openApp, piFlags);

        Intent callIntent = new Intent(Intent.ACTION_DIAL,
            Uri.parse("tel:" + ecNum));
        PendingIntent callPI = PendingIntent.getActivity(
            ctx, EMERGENCY_NOTIF_ID + 1, callIntent, piFlags);

        String bigText = ecName + ": " + ecNum;
        if (!allergies.isEmpty()) bigText += "\n⚠️ Allergic to: " + allergies;

        NotificationCompat.Builder b = new NotificationCompat.Builder(
                ctx, MainActivity.CHANNEL_ID_EMERGENCY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 Emergency — " + patientName)
            .setContentText(ecName + ": " + ecNum)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPI)
            .addAction(android.R.drawable.ic_menu_call, "📞 Call " + ecName, callPI);

        try {
            NotificationManagerCompat.from(ctx).notify(EMERGENCY_NOTIF_ID, b.build());
            call.resolve();
        } catch (Exception e) { call.reject("Failed: " + e.getMessage()); }
    }

    // ── Cancel emergency notification ─────────────────────────────────────────
    @PluginMethod
    public void cancelEmergencyNotification(PluginCall call) {
        try {
            NotificationManager nm = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(EMERGENCY_NOTIF_ID);
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    // ── Open notification settings ────────────────────────────────────────────
    @PluginMethod
    public void openSettings(PluginCall call) {
        Context ctx = getContext();
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    private static String safe(String s, String fb) {
        return (s != null && !s.isEmpty()) ? s : fb;
    }
}
