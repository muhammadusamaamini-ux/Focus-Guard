package com.usama.focusguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The full-screen wall shown when something is blocked. */
public class BlockActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView icon;
    private TextView title;
    private TextView message;
    private TextView timer;
    private Button emBtn;
    private TextView emNote;
    private Button homeBtn;
    private String reason = Rules.FOCUS;
    private String pkg;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor(Ui.DARK));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(Color.parseColor(Ui.DARK));
        int pad = Ui.dp(this, 32);
        col.setPadding(pad, pad, pad, pad);

        icon = centered(Ui.text(this, "🔒", 56, Ui.GOLD, false));
        title = centered(Ui.text(this, "", 30, Ui.LIGHT, true));
        message = centered(Ui.text(this, "", 17, Ui.SOFT, false));
        timer = centered(Ui.text(this, "", 48, Ui.GOLD, true));
        timer.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        homeBtn = Ui.button(this, "Back to studying", true);
        homeBtn.setOnClickListener(v -> goHome());

        emBtn = Ui.button(this, "", false);
        emBtn.setBackground(Ui.round(this, "#2E5248", 12));
        emBtn.setOnClickListener(v -> confirmEmergency());
        emNote = centered(Ui.text(this, "", 13, Ui.SOFT, false));

        col.addView(icon);
        col.addView(title, spaced(12));
        col.addView(message, spaced(8));
        col.addView(timer, spaced(20));
        col.addView(homeBtn, spaced(32));
        col.addView(emBtn, spaced(28));
        col.addView(emNote, spaced(6));
        setContentView(col);

        readIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
        update();
    }

    private void readIntent(Intent i) {
        String r = i.getStringExtra("reason");
        reason = r == null ? Rules.FOCUS : r;
        pkg = i.getStringExtra("pkg");
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void update() {
        long now = System.currentTimeMillis();
        emBtn.setVisibility(View.GONE);
        emNote.setVisibility(View.GONE);
        homeBtn.setText("Back to studying");

        if ("limit".equals(reason)) {
            Integer lim = pkg == null ? null : Prefs.getLimits(this).get(pkg);
            icon.setText("⏳");
            title.setText("Daily limit reached");
            message.setText((pkg == null ? "This app" : Apps.label(this, pkg)) + ": you've used your "
                    + (lim == null ? "" : lim + " ") + "minutes for today.\nIt opens again after midnight.");
            timer.setText("");
            return;
        }
        if ("break".equals(reason)) {
            Integer lim = pkg == null ? null : Prefs.getLimits(this).get(pkg);
            long until = pkg == null ? 0L : Rules.breakUntil(this, pkg);
            String name = pkg == null ? "This app" : Apps.label(this, pkg);
            icon.setText("☕");
            if (until > 0L) {
                long used = pkg == null ? 0L : Prefs.getUsage(this, pkg);
                long left = lim == null ? 0L : Math.max(0L, lim * Rules.MIN - used);
                title.setText("Session over");
                message.setText(name + " is on a 1-hour break.\nIt opens again at "
                        + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(until))
                        + (lim == null ? "" : " for up to " + Prefs.duration(Math.min(left, Rules.sessionLength(lim))))
                        + ".\nTime left:");
                timer.setText(Prefs.format(until - now));
            } else {
                title.setText("Break is over");
                message.setText(name + " can be used again.");
                timer.setText("");
                homeBtn.setText("Done");
            }
            return;
        }
        if ("guard".equals(reason)) {
            icon.setText("🔒");
            title.setText("Locked for now");
            message.setText("Focus Guard's settings can't be changed or switched off while focus time, "
                    + "bedtime or your weekly plan is on.");
            timer.setText("");
            return;
        }

        String mode = Rules.mode(this);
        if (mode == null) {
            icon.setText("✅");
            title.setText("Unlocked");
            message.setText("Blocking is over. Everything is open again.");
            timer.setText("");
            homeBtn.setText("Done");
            return;
        }

        if (Rules.FOCUS.equals(mode)) {
            icon.setText("🔒");
            title.setText("Stay focused");
            message.setText("This is blocked during your focus time.\nTime left:");
            timer.setText(Prefs.format(Rules.focusLeft(this)));
        } else {
            icon.setText("🌙");
            title.setText("It's bedtime");
            message.setText("Distracting apps are blocked until "
                    + Prefs.clock(this, Prefs.sleepEnd(this)) + ".\nTime left:");
            timer.setText(Prefs.format(Rules.sleepEndsAt(this) - now));
        }

        // Emergency minutes
        long lenMin = Rules.emLength(mode) / Rules.MIN;
        String per = Rules.SLEEP.equals(mode) ? "once an hour" : "once every 20 minutes";
        emBtn.setVisibility(View.VISIBLE);
        emNote.setVisibility(View.VISIBLE);
        if (Rules.emergencyOn(this)) {
            emBtn.setText("Emergency time is on");
            Ui.enable(emBtn, false);
            emNote.setText(Prefs.format(Prefs.getLong(this, "em_until") - now) + " left");
        } else if (Rules.emAvailable(this, mode)) {
            emBtn.setText("Emergency: open for " + lenMin + " min");
            Ui.enable(emBtn, true);
            emNote.setText("You can use this " + per + ".");
        } else {
            emBtn.setText("Emergency used");
            Ui.enable(emBtn, false);
            emNote.setText("Next emergency in " + Prefs.format(Rules.emAvailableAt(this, mode) - now));
        }
    }

    private void confirmEmergency() {
        final String mode = Rules.mode(this);
        if (mode == null || Rules.emergencyOn(this) || !Rules.emAvailable(this, mode)) return;
        long lenMin = Rules.emLength(mode) / Rules.MIN;
        long perMin = Rules.emPeriod(mode) / Rules.MIN;
        new AlertDialog.Builder(this)
                .setTitle("Use " + lenMin + " emergency minutes?")
                .setMessage("Blocking pauses for " + lenMin + " minutes, then comes back on by itself. "
                        + "Your next emergency will be available in " + perMin + " minutes.")
                .setPositiveButton("Use now", (d, w) -> {
                    Rules.useEmergency(this, mode);
                    Intent open = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
                    if (open != null) {
                        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(open);
                    }
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    private TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout.LayoutParams spaced(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, topDp);
        return lp;
    }
}
