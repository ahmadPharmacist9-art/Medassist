package com.medremind.pk;

import android.app.KeyguardManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * AlarmActivity — Full-screen alarm shown on lock screen.
 *
 * Place at:
 * C:\medremind-pk.html\android\app\src\main\java\com\medremind\pk\AlarmActivity.java
 */
public class AlarmActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private Vibrator    vibrator;
    private Handler     autoFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen and wake the screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km =
                (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON    |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON    |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        String medName  = def(getIntent().getStringExtra("medName"),    "Medicine");
        String strength = def(getIntent().getStringExtra("medStrength"),"");
        String time     = def(getIntent().getStringExtra("schedTime"),  "");
        String instruct = def(getIntent().getStringExtra("instructions"),"");
        String patient  = def(getIntent().getStringExtra("patientName"),"Patient");

        // Build UI
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0a0f1e);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(56, 100, 56, 60);

        tv(root, "💊",        72, 0xFFf0f4ff, 0,0,0,16, false);
        tv(root, patient,     15, 0xFF00d68f,  0,0,0,6,  false);
        tv(root, medName,     38, 0xFFf0f4ff,  0,0,0,4,  true);
        tv(root, strength,    20, 0xFFa8b3cf,  0,0,0,4,  false);
        tv(root, "⏰  "+time, 17, 0xFFffb800,  0,0,0, instruct.isEmpty()?28:6, false);
        if (!instruct.isEmpty())
            tv(root, "⚠️  "+instruct, 13, 0xFFffb800, 0,0,0,28, false);

        btn(root, "✅   Take Now",     0xFF00d68f, 0xFF0a0f1e, 148, 14, this::done);
        btn(root, "⏰   30 Min Later", 0xFF1e2740, 0xFFf0f4ff, 124, 10, this::done);
        btn(root, "❌   Skip",         0x00000000, 0xFF64748b, 100,  0, this::done);

        setContentView(root);
        playAlarm();
        vibrate();

        // Auto-dismiss after 60 seconds
        autoFinish = new Handler(Looper.getMainLooper());
        autoFinish.postDelayed(this::done, 60_000);
    }

    private void done() { stopAll(); finish(); }

    private static String def(String s, String fb) {
        return (s != null && !s.isEmpty()) ? s : fb;
    }

    private void tv(LinearLayout p, String txt, float sp, int color,
                    int l, int t, int r, int b, boolean bold) {
        TextView v = new TextView(this);
        v.setText(txt); v.setTextSize(sp); v.setTextColor(color);
        v.setGravity(Gravity.CENTER); v.setPadding(l,t,r,b);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.addView(v);
    }

    private void btn(LinearLayout p, String label, int bg, int fg,
                     int h, int mb, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(17); b.setTextColor(fg);
        b.setBackgroundColor(bg);
        if (bg != 0x00000000)
            b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(0, 0, 0, mb);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        p.addView(b);
    }

    private void playAlarm() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null)
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) { /* silent */ }
    }

    private void vibrate() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;
            long[] pattern = {0, 700, 300, 700, 300, 700};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            else
                vibrator.vibrate(pattern, 0);
        } catch (Exception e) { /* silent */ }
    }

    private void stopAll() {
        try {
            if (mediaPlayer  != null) {
                mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null;
            }
            if (vibrator     != null) { vibrator.cancel(); vibrator = null; }
            if (autoFinish   != null)
                autoFinish.removeCallbacksAndMessages(null);
        } catch (Exception e) { /* silent */ }
    }

    @Override protected void onDestroy() { super.onDestroy(); stopAll(); }
}
