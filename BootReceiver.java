package com.medremind.pk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * BootReceiver — restarts background service after phone reboot
 * 
 * CRITICAL: Also reschedules all saved alarms from persistent storage.
 * AlarmManager alarms are cleared on reboot, so we must reload them.
 * 
 * Place at:
 * C:\medremind-pk.html\android\app\src\main\java\com\medremind\pk\BootReceiver.java
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) &&
            !"android.intent.action.QUICKBOOT_POWERON".equals(action) &&
            !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) return;

        // Step 1: Restart background service
        try {
            Intent svc = new Intent(context, MedReminderService.class);
            svc.setAction(MedReminderService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(svc);
            else
                context.startService(svc);
        } catch (Exception ignored) {}

        // Step 2: Launch app to reschedule alarms via MainActivity
        // MainActivity.rescheduleStoredAlarms() will restore saved alarms
        try {
            Intent launch = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_SINGLE_TOP);
                launch.putExtra("reschedule_alarms", true); // Signal to reschedule
                context.startActivity(launch);
            }
        } catch (Exception ignored) {}
    }
}
