package com.usama.focusguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The full-screen message shown when you open something that's blocked: during focus,
 * at bedtime, when a timer has run out, or during a session break. It's drawn by the
 * blocker itself, so it always appears on top, on every phone.
 */
public class BlockOverlay {

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private TextView icon;
    private TextView title;
    private TextView message;
    private TextView timer;
    private Button homeBtn;
    private Button emBtn;
    private TextView emNote;

    String reason;
    String pkg;
    private long confirmUntil;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000L);
        }
    };

    public BlockOverlay(AccessibilityService svc) {
        this.svc = svc;
        this.wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isShowing() { return root != null; }

    /** Returns false if the overlay couldn't be shown. */
    public boolean show(String reason, String pkg) {
        this.reason = reason;
        this.pkg = pkg;
        this.confirmUntil = 0L;
        if (root == null) {
            build();
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE);
            lp.gravity = Gravity.CENTER;
            try {
                wm.addView(root, lp);
            } catch (Exception e) {
                root = null;
                return false;
            }
            root.requestFocus();
            handler.post(tick);
        }
        update();
        return true;
    }

    public void hide() {
        handler.removeCallbacks(tick);
        if (root != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            root = null;
        }
    }

    private void goHome() {
        hide();
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
    }

    // ---------- layout ----------

    private void build() {
        Context c = svc;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor(Ui.DARK));
        int pad = Ui.dp(c, 32);
        root.setPadding(pad, pad, pad, pad);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                goHome();
                return true;
            }
            return code == KeyEvent.KEYCODE_BACK;
        });

        icon = centered(Ui.text(c, "🔒", 56, Ui.GOLD, false));
        title = centered(Ui.text(c, "", 28, Ui.LIGHT, true));
        message = centered(Ui.text(c, "", 17, Ui.SOFT, false));
        timer = centered(Ui.text(c, "", 46, Ui.GOLD, true));
        timer.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        homeBtn = Ui.button(c, "Back to studying", true);
        homeBtn.setOnClickListener(v -> goHome());

        emBtn = Ui.button(c, "", false);
        emBtn.setTextColor(Color.parseColor(Ui.LIGHT));
        emBtn.setBackground(Ui.round(c, "#2E5248", 12));
        emBtn.setOnClickListener(v -> emergencyTapped());
        emNote = centered(Ui.text(c, "", 13, Ui.SOFT, false));

        root.addView(icon);
        root.addView(title, spaced(12));
        root.addView(message, spaced(10));
        root.addView(timer, spaced(18));
        root.addView(homeBtn, spaced(30));
        root.addView(emBtn, spaced(26));
        root.addView(emNote, spaced(6));
    }

    private TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout.LayoutParams spaced(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(svc, topDp);
        return lp;
    }

    // ---------- content ----------

    private String appName() {
        return pkg == null ? "This app" : Apps.label(svc, pkg);
    }

    private String clock(long millis) {
        return android.text.format.DateFormat.getTimeFormat(svc).format(new java.util.Date(millis));
    }

    private void update() {
        if (root == null) return;
        Context c = svc;
        long now = System.currentTimeMillis();
        emBtn.setVisibility(View.GONE);
        emNote.setVisibility(View.GONE);
        homeBtn.setText("Back to studying");

        if ("limit".equals(reason)) {
            Integer lim = pkg == null ? null : Prefs.getLimits(c).get(pkg);
            icon.setText("⏳");
            title.setText("App timer ran out");
            message.setText(appName() + " is paused for the rest of today. You've used your "
                    + (lim == null ? "" : lim + " ") + "minutes.\n\nIt opens again tomorrow. Your timers are "
                    + "locked for the week, so they can't be increased.");
            timer.setText("");
            homeBtn.setText("OK");
            return;
        }
        if ("break".equals(reason)) {
            Integer lim = pkg == null ? null : Prefs.getLimits(c).get(pkg);
            long until = pkg == null ? 0L : Rules.breakUntil(c, pkg);
            icon.setText("☕");
            if (until > 0L) {
                long left = lim == null ? 0L : Math.max(0L, lim * Rules.MIN - Prefs.getUsage(c, pkg));
                title.setText("Session over");
                message.setText(appName() + " is taking a 1-hour break.\nIt opens again at " + clock(until)
                        + (lim == null ? "" : " for up to " + Prefs.duration(Math.min(left, Rules.sessionLength(lim))))
                        + ".\n\nTime left:");
                timer.setText(Prefs.format(until - now));
            } else {
                title.setText("Break is over");
                message.setText(appName() + " can be used again.");
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
            homeBtn.setText("OK");
            return;
        }

        String mode = Rules.mode(c);
        if (mode == null) {
            icon.setText("✅");
            title.setText("Unlocked");
            message.setText("Blocking is over. Everything is open again.");
            timer.setText("");
            homeBtn.setText("Done");
            return;
        }

        if (Rules.FOCUS.equals(mode)) {
            icon.setText("📚");
            title.setText("You're in study mode");
            message.setText(appName() + " is blocked during study mode.\nIt opens again at "
                    + clock(Rules.focusEndsAt(c)) + ".\n\nTime left:");
            timer.setText(Prefs.format(Rules.focusLeft(c)));
        } else {
            icon.setText("🌙");
            title.setText("It's bedtime");
            message.setText(appName() + " is blocked at bedtime.\nIt opens again at "
                    + Prefs.clock(c, Prefs.sleepEnd(c)) + ".\n\nTime left:");
            timer.setText(Prefs.format(Rules.sleepEndsAt(c) - now));
        }

        // Emergency minutes
        long lenMin = Rules.emLength(mode) / Rules.MIN;
        String per = Rules.SLEEP.equals(mode) ? "once an hour" : "once every 20 minutes";
        emBtn.setVisibility(View.VISIBLE);
        emNote.setVisibility(View.VISIBLE);
        if (Rules.emergencyOn(c)) {
            emBtn.setText("Emergency time is on");
            Ui.enable(emBtn, false);
            emNote.setText(Prefs.format(Prefs.getLong(c, "em_until") - now) + " left");
        } else if (Rules.emAvailable(c, mode)) {
            boolean confirming = now < confirmUntil;
            emBtn.setText(confirming ? "Tap again to open for " + lenMin + " min"
                    : "🚨 Emergency: open for " + lenMin + " min");
            Ui.enable(emBtn, true);
            emNote.setText(confirming
                    ? "Blocking pauses for " + lenMin + " min, then comes back on by itself."
                    : "You can use this " + per + ".");
        } else {
            emBtn.setText("Emergency used");
            Ui.enable(emBtn, false);
            emNote.setText("Next emergency in " + Prefs.format(Rules.emAvailableAt(c, mode) - now));
        }
    }

    /** First tap asks for confirmation; a second tap within 5 seconds opens the app. */
    private void emergencyTapped() {
        String mode = Rules.mode(svc);
        if (mode == null || Rules.emergencyOn(svc) || !Rules.emAvailable(svc, mode)) return;
        long now = System.currentTimeMillis();
        if (now >= confirmUntil) {
            confirmUntil = now + 5_000L;
            update();
            return;
        }
        Rules.useEmergency(svc, mode);
        String open = pkg;
        hide();
        Intent i = open == null ? null : svc.getPackageManager().getLaunchIntentForPackage(open);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                svc.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }
}
