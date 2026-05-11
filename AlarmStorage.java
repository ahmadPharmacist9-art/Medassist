package com.medremind.pk;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AlarmStorage — Persists medicine alarms to SharedPreferences
 * 
 * Ensures alarms survive:
 * - Phone reboot (all AlarmManager alarms are cleared on reboot)
 * - Force stop (app killed)
 * - System update
 * - Doze mode (alarms can be deferred)
 */
public class AlarmStorage {

    private static final String PREFS = "med_alarms";
    private static final String KEY_ALARMS = "scheduled_alarms";
    private static final String KEY_PERMISSION = "exact_alarm_permission";

    /**
     * Save all alarms to persistent storage
     */
    public static void saveAlarms(Context ctx, JSONArray alarms) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putString(KEY_ALARMS, alarms.toString()).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Load all alarms from persistent storage
     */
    public static JSONArray loadAlarms(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String json = p.getString(KEY_ALARMS, "[]");
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /**
     * Clear all saved alarms
     */
    public static void clearAlarms(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().remove(KEY_ALARMS).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Save exact alarm permission status
     */
    public static void setExactAlarmPermission(Context ctx, boolean granted) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putBoolean(KEY_PERMISSION, granted).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Get exact alarm permission status
     */
    public static boolean hasExactAlarmPermission(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return p.getBoolean(KEY_PERMISSION, false);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Format alarm for storage (from MedRemindPlugin alarm object)
     */
    public static JSONObject formatAlarm(
            int alarmId, String medName, String strength, String schedTime,
            String instructions, String patientName, int hour, int minute, int dayOffset) {
        JSONObject a = new JSONObject();
        try {
            a.put("alarmId", alarmId);
            a.put("medName", medName);
            a.put("medStrength", strength);
            a.put("schedTime", schedTime);
            a.put("instructions", instructions);
            a.put("patientName", patientName);
            a.put("hour", hour);
            a.put("minute", minute);
            a.put("dayOffset", dayOffset);
        } catch (Exception ignored) {}
        return a;
    }
}