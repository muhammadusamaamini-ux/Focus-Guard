package com.usama.focusguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int[] PRESETS = {25, 30, 45, 60, 90};
    private static final String NOTIF_PERM = "android.permission.POST_NOTIFICATIONS";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> chips = new ArrayList<>();

    private LinearLayout accCard;
    private LinearLayout notifCard;
    private LinearLayout weekCard;
    private LinearLayout planBody;
    private TextView status;
    private TextView statusSub;
    private EditText minutesInput;
    private Button startBtn;
    private TextView listSummary;
    private Switch grayFocus;
    private LinearLayout grayCard;
    private int ticks;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            if (++ticks % 15 == 0) refreshPlan();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout col = Ui.page(this);

        col.addView(Ui.text(this, "Focus Guard", 30, Ui.INK, true));
        col.addView(Ui.text(this, "Block distractions while you study.", 15, Ui.MUTED, false),
                Ui.top(this, 2));

        // --- Blocker switched off ---
        accCard = Ui.card(this, Ui.WARN);
        accCard.addView(Ui.text(this, "The blocker is switched off", 16, Ui.INK, true));
        accCard.addView(Ui.text(this, "Turn on \"Focus Guard\" in Accessibility settings so it can block "
                + "apps and websites. If the switch is greyed out: tap Open App info, then ⋮ (top right), "
                + "then \"Allow restricted settings\", and try again.", 14, Ui.INK, false), Ui.top(this, 6));
        LinearLayout accRow = Ui.row(this);
        Button accBtn = Ui.button(this, "Accessibility", false);
        accBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button infoBtn = Ui.button(this, "App info", false);
        infoBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName()))));
        accRow.addView(accBtn, Ui.weight(this, 1f, 0));
        accRow.addView(infoBtn, Ui.weight(this, 1f, 8));
        accCard.addView(accRow, Ui.top(this, 12));
        col.addView(accCard, Ui.top(this, 16));

        // --- Notifications not allowed ---
        notifCard = Ui.card(this, Ui.WARN);
        notifCard.addView(Ui.text(this, "Allow notifications", 16, Ui.INK, true));
        notifCard.addView(Ui.text(this, "So you can start focus straight from your notification bar, "
                + "without opening the app.", 14, Ui.INK, false), Ui.top(this, 6));
        Button notifBtn = Ui.button(this, "Allow notifications", false);
        notifBtn.setOnClickListener(v -> askNotifications(true));
        notifCard.addView(notifBtn, Ui.top(this, 12));
        col.addView(notifCard, Ui.top(this, 12));

        // --- Status ---
        LinearLayout statusCard = Ui.card(this, Ui.INK);
        status = Ui.text(this, "", 20, Ui.PAPER, true);
        statusSub = Ui.text(this, "", 14, Ui.SOFT, false);
        statusCard.addView(status);
        statusCard.addView(statusSub, Ui.top(this, 4));
        col.addView(statusCard, Ui.top(this, 16));

        // --- Focus (at the top, no scrolling needed) ---
        LinearLayout focusCard = Ui.card(this, Ui.CARD);
        focusCard.addView(Ui.text(this, "Focus time (minutes)", 17, Ui.INK, true));
        LinearLayout chipRow = Ui.row(this);
        for (int i = 0; i < PRESETS.length; i++) {
            final int p = PRESETS[i];
            Button b = Ui.chip(this, String.valueOf(p));
            b.setOnClickListener(v -> minutesInput.setText(String.valueOf(p)));
            chipRow.addView(b, Ui.weight(this, 1f, i == 0 ? 0 : 6));
            chips.add(b);
        }
        focusCard.addView(chipRow, Ui.top(this, 10));

        LinearLayout startRow = Ui.row(this);
        minutesInput = new EditText(this);
        minutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutesInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        minutesInput.setGravity(Gravity.CENTER);
        minutesInput.setText(String.valueOf(Prefs.getMinutes(this)));
        Ui.styleInput(this, minutesInput);
        minutesInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { highlightChips(); }
        });
        startBtn = Ui.button(this, "Start focus", true);
        startBtn.setOnClickListener(v -> confirmStart());
        startRow.addView(minutesInput, new LinearLayout.LayoutParams(Ui.dp(this, 88),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        startRow.addView(startBtn, Ui.weight(this, 1f, 12));
        focusCard.addView(startRow, Ui.top(this, 12));

        grayFocus = new Switch(this);
        grayFocus.setText("Grayscale screen during focus");
        grayFocus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        grayFocus.setTextColor(Color.parseColor(Ui.INK));
        grayFocus.setChecked(Gray.focusWanted(this));
        grayFocus.setOnCheckedChangeListener((b, on) -> {
            Prefs.putBool(this, "gray_focus", on);
            refreshStatus();
        });
        focusCard.addView(grayFocus, Ui.top(this, 12));
        col.addView(focusCard, Ui.top(this, 12));

        // --- One-time grayscale setup ---
        grayCard = Ui.card(this, Ui.WARN);
        grayCard.addView(Ui.text(this, "One-time setup for grayscale", 16, Ui.INK, true));
        grayCard.addView(Ui.text(this, "Android only lets an app turn the screen gray after you allow it "
                + "once from a computer, using this command:", 14, Ui.INK, false), Ui.top(this, 6));
        TextView cmd = Ui.text(this, Gray.GRANT_COMMAND, 13, Ui.INK, false);
        cmd.setTypeface(Typeface.MONOSPACE);
        cmd.setTextIsSelectable(true);
        cmd.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        cmd.setBackground(Ui.round(this, Ui.PAPER, 8));
        grayCard.addView(cmd, Ui.top(this, 8));
        Button copy = Ui.button(this, "Copy command", false);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("command", Gray.GRANT_COMMAND));
            Rules.toast(this, "Command copied.");
        });
        grayCard.addView(copy, Ui.top(this, 10));
        col.addView(grayCard, Ui.top(this, 12));

        // --- Week over ---
        weekCard = Ui.card(this, Ui.WARN);
        weekCard.addView(Ui.text(this, "Your plan's week is over", 16, Ui.INK, true));
        weekCard.addView(Ui.text(this, "Keep the same daily limits and bedtime for another 7 days, "
                + "or change them now. Until you choose, your current limits stay on.", 14, Ui.INK, false),
                Ui.top(this, 6));
        LinearLayout weekRow = Ui.row(this);
        Button keep = Ui.button(this, "Keep for 7 days", false);
        keep.setOnClickListener(v -> {
            Rules.lockPlan(this);
            Rules.toast(this, "Same plan locked for another 7 days.");
            refreshPlan();
            refreshStatus();
        });
        Button change = Ui.button(this, "Change it", false);
        change.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        weekRow.addView(keep, Ui.weight(this, 1f, 0));
        weekRow.addView(change, Ui.weight(this, 1f, 8));
        weekCard.addView(weekRow, Ui.top(this, 12));
        col.addView(weekCard, Ui.top(this, 12));

        // --- Daily limits & bedtime ---
        LinearLayout planCard = Ui.card(this, Ui.CARD);
        planCard.addView(Ui.text(this, "Daily limits & bedtime", 17, Ui.INK, true));
        planBody = new LinearLayout(this);
        planBody.setOrientation(LinearLayout.VERTICAL);
        planCard.addView(planBody, Ui.top(this, 8));
        col.addView(planCard, Ui.top(this, 12));

        // --- Distracting list ---
        LinearLayout listCard = Ui.card(this, Ui.CARD);
        listCard.addView(Ui.text(this, "Distracting apps & websites", 17, Ui.INK, true));
        listCard.addView(Ui.text(this, "Blocked during focus time and bedtime.", 14, Ui.MUTED, false),
                Ui.top(this, 2));
        listSummary = Ui.text(this, "", 15, Ui.INK, false);
        listCard.addView(listSummary, Ui.top(this, 8));
        Button editList = Ui.button(this, "Edit list", false);
        editList.setOnClickListener(v -> startActivity(new Intent(this, BlockListActivity.class)));
        listCard.addView(editList, Ui.top(this, 12));
        col.addView(listCard, Ui.top(this, 12));

        // --- Quick start help ---
        LinearLayout tips = Ui.card(this, Ui.CARD);
        tips.addView(Ui.text(this, "Start focus without opening the app", 17, Ui.INK, true));
        tips.addView(Ui.text(this,
                "• Notification bar: tap 30, 45 or 60 min on the Focus Guard notification.\n"
                        + "• Quick Settings: swipe down twice, tap the ✏️ edit button, and drag the "
                        + "\"Focus\" tile into your tiles. Tap it to start.\n"
                        + "• Home screen: long-press the Focus Guard icon and pick a time.",
                14, Ui.INK, false), Ui.top(this, 6));
        col.addView(tips, Ui.top(this, 12));

        // --- Updates ---
        LinearLayout upd = Ui.card(this, Ui.CARD);
        upd.addView(Ui.text(this, "App updates", 17, Ui.INK, true));
        upd.addView(Ui.text(this, "Installed: build " + Updater.installedBuild(this), 14, Ui.MUTED, false),
                Ui.top(this, 2));
        Button check = Ui.button(this, "Check for updates", false);
        check.setOnClickListener(v -> Updater.check(this));
        upd.addView(check, Ui.top(this, 12));
        col.addView(upd, Ui.top(this, 12));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsNotifications() && !Prefs.getBool(this, "asked_notif")) {
            Prefs.putBool(this, "asked_notif", true);
            askNotifications(false);
        }
        Notifier.update(this, true);
        refreshPlan();
        refreshStatus();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        Notifier.update(this, true);
        refreshStatus();
    }

    // ---------- refresh ----------

    private void refreshStatus() {
        accCard.setVisibility(Rules.blockerOn(this) ? View.GONE : View.VISIBLE);
        notifCard.setVisibility(needsNotifications() ? View.VISIBLE : View.GONE);
        weekCard.setVisibility(Rules.weekOver(this) ? View.VISIBLE : View.GONE);

        boolean focus = Rules.focusOn(this);
        long now = System.currentTimeMillis();
        if (Rules.emergencyOn(this)) {
            status.setText("Emergency time · " + Prefs.format(Prefs.getLong(this, "em_until") - now) + " left");
            statusSub.setText("Blocking comes back on automatically.");
        } else if (focus) {
            status.setText("Focus is ON · " + Prefs.format(Prefs.remaining(this)) + " left");
            statusSub.setText("Blocked apps, websites and settings are locked until it ends.");
        } else if (Rules.sleepNow(this)) {
            status.setText("Bedtime 🌙");
            statusSub.setText("Distracting apps are blocked until "
                    + Prefs.clock(this, Prefs.sleepEnd(this)) + ".");
        } else {
            status.setText("Ready to focus");
            statusSub.setText(Rules.planLocked(this)
                    ? "Weekly plan locked until " + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock")) + "."
                    : "Pick a time and tap Start focus.");
        }

        startBtn.setText(focus ? "Focus is on" : "Start focus");
        Ui.enable(startBtn, !focus);
        Ui.enable(minutesInput, !focus);
        for (Button b : chips) Ui.enable(b, !focus);
        Ui.enable(grayFocus, !focus);
        grayCard.setVisibility(Gray.anyWanted(this) && !Gray.canControl(this) ? View.VISIBLE : View.GONE);
        highlightChips();

        int apps = Rules.blockedApps(this).size();
        int sites = Rules.blockedSites(this).size();
        listSummary.setText(apps + (apps == 1 ? " app" : " apps") + " · "
                + sites + (sites == 1 ? " website" : " websites"));
    }

    private void refreshPlan() {
        planBody.removeAllViews();
        if (!Rules.hasPlan(this)) {
            planBody.addView(Ui.text(this, "Give apps like Facebook or TikTok a daily time limit, and set a "
                    + "bedtime when distracting apps are blocked. Once saved, the plan is locked for 7 days.",
                    14, Ui.MUTED, false));
            Button setup = Ui.button(this, "Set up plan", false);
            setup.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
            planBody.addView(setup, Ui.top(this, 12));
            return;
        }

        String bed = Prefs.sleepOn(this)
                ? "🌙 Bedtime: " + Prefs.clock(this, Prefs.sleepStart(this)) + " – "
                + Prefs.clock(this, Prefs.sleepEnd(this))
                : "🌙 Bedtime: off";
        planBody.addView(Ui.text(this, bed, 15, Ui.INK, false));

        for (Map.Entry<String, Integer> e : Prefs.getLimits(this).entrySet()) {
            long used = Prefs.getUsage(this, e.getKey()) / Rules.MIN;
            boolean done = used >= e.getValue();
            String line = "⏳ " + Apps.label(this, e.getKey()) + ": " + Math.min(used, e.getValue())
                    + " of " + e.getValue() + " min used today" + (done ? " · limit reached" : "");
            planBody.addView(Ui.text(this, line, 15, done ? Ui.RED : Ui.INK, false), Ui.top(this, 4));
        }

        boolean locked = Rules.planLocked(this);
        String lockLine = locked
                ? "🔒 Locked until " + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock"))
                : "🔓 Unlocked: you can change it now";
        planBody.addView(Ui.text(this, lockLine, 14, Ui.MUTED, false), Ui.top(this, 8));

        Button open = Ui.button(this, locked ? "View plan" : "Edit plan", false);
        open.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        planBody.addView(open, Ui.top(this, 12));
    }

    private void highlightChips() {
        String v = minutesInput.getText().toString().trim();
        for (int i = 0; i < chips.size(); i++) {
            Ui.setChip(this, chips.get(i), v.equals(String.valueOf(PRESETS[i])));
        }
    }

    // ---------- actions ----------

    private void confirmStart() {
        if (Rules.focusOn(this)) return;
        int minutes;
        try {
            minutes = Integer.parseInt(minutesInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            minutes = 0;
        }
        if (minutes < 1 || minutes > 720) {
            Rules.toast(this, "Enter a focus time between 1 and 720 minutes.");
            return;
        }
        Prefs.setMinutes(this, minutes);
        final int mins = minutes;
        new AlertDialog.Builder(this)
                .setTitle("Start a " + mins + "-minute focus?")
                .setMessage("Until it ends you won't be able to open the blocked apps or websites, "
                        + "change your list, or switch Focus Guard off. You'll get 2 emergency minutes "
                        + "every 20 minutes.")
                .setPositiveButton("Start", (d, w) -> {
                    Rules.startFocus(this, mins);
                    refreshStatus();
                })
                .setNegativeButton("Not yet", null)
                .show();
    }

    private boolean needsNotifications() {
        return Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(NOTIF_PERM) != PackageManager.PERMISSION_GRANTED;
    }

    private void askNotifications(boolean fromButton) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (!fromButton || shouldShowRequestPermissionRationale(NOTIF_PERM)) {
            requestPermissions(new String[]{NOTIF_PERM}, 7);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    @SuppressWarnings("unused")
    private static int color(String hex) { return Color.parseColor(hex); }
}
