package com.usama.focusguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Daily app limits and bedtime. Saving locks them for 7 days. */
public class PlanActivity extends Activity {

    private final Map<String, Integer> limits = new TreeMap<>();
    private boolean sleepOn;
    private int sleepStart;
    private int sleepEnd;
    private boolean locked;

    private Switch sleepSwitch;
    private Button fromBtn;
    private Button toBtn;
    private LinearLayout limitsBox;
    private Button addBtn;
    private List<Apps.Item> appsCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        limits.putAll(Prefs.getLimits(this));
        sleepOn = Prefs.sleepOn(this);
        sleepStart = Prefs.sleepStart(this);
        sleepEnd = Prefs.sleepEnd(this);
        locked = Rules.planLocked(this);

        LinearLayout col = Ui.page(this);
        col.addView(Ui.text(this, "Weekly plan", 26, Ui.INK, true));
        col.addView(Ui.text(this, "Daily app limits and bedtime. Once you save, the plan is locked "
                + "for 7 days. After that you can keep it or change it.", 14, Ui.MUTED, false),
                Ui.top(this, 4));

        if (locked) {
            LinearLayout lockCard = Ui.card(this, Ui.WARN);
            lockCard.addView(Ui.text(this, "🔒 Locked until "
                    + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock"))
                    + ". You can change it after that.", 15, Ui.INK, true));
            col.addView(lockCard, Ui.top(this, 14));
        }

        // --- Bedtime ---
        LinearLayout bedCard = Ui.card(this, Ui.CARD);
        LinearLayout bedHead = Ui.row(this);
        bedHead.addView(Ui.text(this, "Bedtime 🌙", 17, Ui.INK, true), Ui.weight(this, 1f, 0));
        sleepSwitch = new Switch(this);
        sleepSwitch.setChecked(sleepOn);
        sleepSwitch.setOnCheckedChangeListener((b, on) -> {
            sleepOn = on;
            refreshBedtime();
        });
        bedHead.addView(sleepSwitch);
        bedCard.addView(bedHead);
        bedCard.addView(Ui.text(this, "Your distracting apps and websites are blocked between these "
                + "times. You get 5 emergency minutes per hour.", 13, Ui.MUTED, false), Ui.top(this, 4));
        LinearLayout times = Ui.row(this);
        fromBtn = Ui.chip(this, "");
        toBtn = Ui.chip(this, "");
        fromBtn.setOnClickListener(v -> pickTime(true));
        toBtn.setOnClickListener(v -> pickTime(false));
        times.addView(fromBtn, Ui.weight(this, 1f, 0));
        times.addView(toBtn, Ui.weight(this, 1f, 8));
        bedCard.addView(times, Ui.top(this, 10));
        col.addView(bedCard, Ui.top(this, 12));

        // --- Daily limits ---
        LinearLayout limCard = Ui.card(this, Ui.CARD);
        limCard.addView(Ui.text(this, "Daily limits", 17, Ui.INK, true));
        limCard.addView(Ui.text(this, "When an app's time is used up, it stays blocked until midnight.",
                13, Ui.MUTED, false), Ui.top(this, 2));
        limitsBox = new LinearLayout(this);
        limitsBox.setOrientation(LinearLayout.VERTICAL);
        limCard.addView(limitsBox, Ui.top(this, 6));
        addBtn = Ui.button(this, "+ Add an app", false);
        addBtn.setOnClickListener(v -> addApp());
        limCard.addView(addBtn, Ui.top(this, 10));
        col.addView(limCard, Ui.top(this, 12));

        // --- Save ---
        Button save = Ui.button(this, "Save & lock for 7 days", true);
        save.setOnClickListener(v -> confirmSave());
        save.setVisibility(locked ? View.GONE : View.VISIBLE);
        col.addView(save, Ui.top(this, 20));

        refreshBedtime();
        refreshLimits();
        Ui.enable(sleepSwitch, !locked);
        Ui.enable(addBtn, !locked);
    }

    private void refreshBedtime() {
        fromBtn.setText("From " + Prefs.clock(this, sleepStart));
        toBtn.setText("Until " + Prefs.clock(this, sleepEnd));
        Ui.enable(fromBtn, sleepOn && !locked);
        Ui.enable(toBtn, sleepOn && !locked);
    }

    private void pickTime(boolean start) {
        int cur = start ? sleepStart : sleepEnd;
        new TimePickerDialog(this, (view, h, m) -> {
            if (start) sleepStart = h * 60 + m;
            else sleepEnd = h * 60 + m;
            refreshBedtime();
        }, cur / 60, cur % 60, android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    private void refreshLimits() {
        limitsBox.removeAllViews();
        if (limits.isEmpty()) {
            limitsBox.addView(Ui.text(this, "No limits yet.", 14, Ui.MUTED, false));
            return;
        }
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(limits.entrySet());
        rows.sort((a, b) -> Apps.label(this, a.getKey()).compareToIgnoreCase(Apps.label(this, b.getKey())));
        for (Map.Entry<String, Integer> e : rows) {
            final String pkg = e.getKey();
            final String label = Apps.label(this, pkg);
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            row.addView(Ui.text(this, label, 16, Ui.INK, false), Ui.weight(this, 1f, 0));

            Button mins = Ui.chip(this, e.getValue() + " min/day");
            mins.setOnClickListener(v -> askMinutes(pkg, label, limits.get(pkg)));
            Ui.enable(mins, !locked);
            row.addView(mins);

            Button del = Ui.chip(this, "✕");
            del.setOnClickListener(v -> {
                limits.remove(pkg);
                refreshLimits();
            });
            Ui.enable(del, !locked);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Ui.dp(this, 6));
            row.addView(del, lp);
            limitsBox.addView(row);
        }
    }

    private void addApp() {
        if (appsCache != null) {
            showAppPicker();
            return;
        }
        Rules.toast(this, "Loading your apps…");
        new Thread(() -> {
            List<Apps.Item> items = Apps.load(this);
            runOnUiThread(() -> {
                appsCache = items;
                showAppPicker();
            });
        }).start();
    }

    private void showAppPicker() {
        final List<Apps.Item> choices = new ArrayList<>();
        for (Apps.Item it : appsCache) if (!limits.containsKey(it.pkg)) choices.add(it);
        String[] labels = new String[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = choices.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Which app?")
                .setItems(labels, (d, w) -> askMinutes(choices.get(w).pkg, choices.get(w).label, 30))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askMinutes(String pkg, String label, Integer current) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.CENTER);
        input.setText(String.valueOf(current == null ? 30 : current));
        input.setSelectAllOnFocus(true);
        Ui.styleInput(this, input);
        LinearLayout wrap = new LinearLayout(this);
        int p = Ui.dp(this, 20);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(input, Ui.top(this, 0));

        new AlertDialog.Builder(this)
                .setTitle(label + ": minutes per day")
                .setView(wrap)
                .setPositiveButton("OK", (d, w) -> {
                    int m;
                    try {
                        m = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException ex) {
                        m = 0;
                    }
                    if (m < 1 || m > 1440) {
                        Rules.toast(this, "Enter between 1 and 1440 minutes.");
                        return;
                    }
                    limits.put(pkg, m);
                    refreshLimits();
                })
                .setNegativeButton("Cancel", null)
                .show();
        input.requestFocus();
    }

    private void confirmSave() {
        if (locked) return;
        if (limits.isEmpty() && !sleepOn) {
            Prefs.setLimits(this, limits);
            Prefs.setSleep(this, false, sleepStart, sleepEnd);
            Prefs.putLong(this, "plan_lock", 0L);
            Notifier.update(this, true);
            Rules.toast(this, "Plan cleared.");
            finish();
            return;
        }
        if (sleepOn && sleepStart == sleepEnd) {
            Rules.toast(this, "Bedtime start and end can't be the same time.");
            return;
        }
        long until = System.currentTimeMillis() + Rules.WEEK;
        new AlertDialog.Builder(this)
                .setTitle("Lock this plan for 7 days?")
                .setMessage("Until " + Prefs.dateTime(this, until) + " you won't be able to change these "
                        + "limits or your bedtime, remove apps from your distracting list, or switch "
                        + "Focus Guard off. After that, you can keep the plan or change it.")
                .setPositiveButton("Lock it", (d, w) -> {
                    Prefs.setLimits(this, limits);
                    Prefs.setSleep(this, sleepOn, sleepStart, sleepEnd);
                    Rules.lockPlan(this);
                    Notifier.update(this, true);
                    Rules.toast(this, "Plan saved and locked for 7 days.");
                    finish();
                })
                .setNegativeButton("Not yet", null)
                .show();
    }
}
