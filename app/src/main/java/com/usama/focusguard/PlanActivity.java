package com.usama.focusguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
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

/** Daily app limits, bedtime and focus schedule. Saving locks them for 7 days. */
public class PlanActivity extends Activity {

    private final Map<String, Integer> limits = new TreeMap<>();
    private boolean locked;

    // Bedtime
    private boolean sleepOn;
    private int sleepStart;
    private int sleepEnd;
    private Switch sleepSwitch;
    private Button sleepFrom;
    private Button sleepTo;
    private DayPicker sleepDays;
    private Switch graySwitch;
    private Switch dndSwitch;

    // Focus schedule
    private boolean schedOn;
    private int schedStart;
    private int schedEnd;
    private Switch schedSwitch;
    private Button schedFrom;
    private Button schedTo;
    private DayPicker schedDays;

    private LinearLayout limitsBox;
    private Button addBtn;
    private List<Apps.Item> appsCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyTheme(this);
        super.onCreate(savedInstanceState);
        limits.putAll(Prefs.getLimits(this));
        locked = Rules.planLocked(this);
        sleepOn = Prefs.sleepOn(this);
        sleepStart = Prefs.sleepStart(this);
        sleepEnd = Prefs.sleepEnd(this);
        schedOn = Prefs.schedOn(this);
        schedStart = Prefs.schedStart(this);
        schedEnd = Prefs.schedEnd(this);

        LinearLayout col = Ui.page(this);
        col.addView(Ui.text(this, "Limits & bedtime", 26, Ui.INK, true));
        col.addView(Ui.text(this, "App timers, bedtime and focus schedule. Once you save, the plan is "
                + "locked for 7 days. After that you can keep it or change it.", 14, Ui.MUTED, false),
                Ui.top(this, 4));

        if (locked) {
            LinearLayout lockCard = Ui.card(this, Ui.WARN);
            lockCard.addView(Ui.text(this, "🔒 Locked until "
                    + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock"))
                    + ". You can change it after that.", 15, Ui.INK, true));
            col.addView(lockCard, Ui.top(this, 14));
        }

        // --- Daily limits (app timers) ---
        LinearLayout limCard = Ui.card(this, Ui.CARD);
        limCard.addView(Ui.text(this, "⏳ App timers", 17, Ui.INK, true));
        limCard.addView(Ui.text(this, "A daily time limit per app. When it's used up, the app is blocked "
                + "until midnight.", 13, Ui.MUTED, false), Ui.top(this, 2));
        limitsBox = new LinearLayout(this);
        limitsBox.setOrientation(LinearLayout.VERTICAL);
        limCard.addView(limitsBox, Ui.top(this, 6));
        addBtn = Ui.button(this, "+ Add an app timer", false);
        addBtn.setOnClickListener(v -> addApp());
        limCard.addView(addBtn, Ui.top(this, 10));
        col.addView(limCard, Ui.top(this, 12));

        // --- Bedtime ---
        LinearLayout bedCard = Ui.card(this, Ui.CARD);
        sleepSwitch = header(bedCard, "🌙 Bedtime", sleepOn);
        sleepSwitch.setOnCheckedChangeListener((b, on) -> {
            sleepOn = on;
            refreshEnabled();
        });
        bedCard.addView(Ui.text(this, "Your distracting apps and websites are blocked between these times. "
                + "5 emergency minutes per hour.", 13, Ui.MUTED, false), Ui.top(this, 4));
        LinearLayout bedTimes = Ui.row(this);
        sleepFrom = Ui.chip(this, "");
        sleepTo = Ui.chip(this, "");
        sleepFrom.setOnClickListener(v -> pickTime(0));
        sleepTo.setOnClickListener(v -> pickTime(1));
        bedTimes.addView(sleepFrom, Ui.weight(this, 1f, 0));
        bedTimes.addView(sleepTo, Ui.weight(this, 1f, 8));
        bedCard.addView(bedTimes, Ui.top(this, 10));
        sleepDays = new DayPicker(this, Prefs.sleepDays(this));
        bedCard.addView(sleepDays, Ui.top(this, 8));
        graySwitch = toggle(bedCard, "Grayscale screen at bedtime", Gray.sleepWanted(this));
        dndSwitch = toggle(bedCard, "Do Not Disturb at bedtime", Dnd.wanted(this));
        dndSwitch.setOnCheckedChangeListener((b, on) -> {
            if (on && !Dnd.canControl(this)) askDndAccess();
        });
        col.addView(bedCard, Ui.top(this, 12));

        // --- Focus schedule ---
        LinearLayout schCard = Ui.card(this, Ui.CARD);
        schedSwitch = header(schCard, "🎯 Focus schedule", schedOn);
        schedSwitch.setOnCheckedChangeListener((b, on) -> {
            schedOn = on;
            refreshEnabled();
        });
        schCard.addView(Ui.text(this, "Focus turns on by itself at these times, for example your study "
                + "hours. 2 emergency minutes every 20 minutes.", 13, Ui.MUTED, false), Ui.top(this, 4));
        LinearLayout schTimes = Ui.row(this);
        schedFrom = Ui.chip(this, "");
        schedTo = Ui.chip(this, "");
        schedFrom.setOnClickListener(v -> pickTime(2));
        schedTo.setOnClickListener(v -> pickTime(3));
        schTimes.addView(schedFrom, Ui.weight(this, 1f, 0));
        schTimes.addView(schedTo, Ui.weight(this, 1f, 8));
        schCard.addView(schTimes, Ui.top(this, 10));
        schedDays = new DayPicker(this, Prefs.schedDays(this));
        schCard.addView(schedDays, Ui.top(this, 8));
        col.addView(schCard, Ui.top(this, 12));

        // --- Save ---
        Button save = Ui.button(this, "Save & lock for 7 days", true);
        save.setOnClickListener(v -> confirmSave());
        save.setVisibility(locked ? View.GONE : View.VISIBLE);
        col.addView(save, Ui.top(this, 20));

        refreshLimits();
        refreshEnabled();

        // Opened from a specific row on the Home screen: jump to that part.
        String section = getIntent().getStringExtra("section");
        final View target = "bedtime".equals(section) ? bedCard
                : "schedule".equals(section) ? schCard : null;
        if (target != null) {
            col.post(() -> ((android.widget.ScrollView) col.getParent())
                    .smoothScrollTo(0, Math.max(0, target.getTop() - Ui.dp(this, 12))));
        }
    }

    private Switch header(LinearLayout card, String title, boolean on) {
        LinearLayout head = Ui.row(this);
        head.addView(Ui.text(this, title, 17, Ui.INK, true), Ui.weight(this, 1f, 0));
        Switch s = new Switch(this);
        s.setChecked(on);
        head.addView(s);
        card.addView(head);
        return s;
    }

    private Switch toggle(LinearLayout card, String label, boolean on) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setTextColor(Color.parseColor(Ui.INK));
        s.setChecked(on);
        card.addView(s, Ui.top(this, 12));
        return s;
    }

    private void refreshEnabled() {
        sleepFrom.setText("From " + Prefs.clock(this, sleepStart));
        sleepTo.setText("Until " + Prefs.clock(this, sleepEnd));
        schedFrom.setText("From " + Prefs.clock(this, schedStart));
        schedTo.setText("Until " + Prefs.clock(this, schedEnd));

        Ui.enable(sleepSwitch, !locked);
        Ui.enable(schedSwitch, !locked);
        Ui.enable(addBtn, !locked);
        boolean bed = sleepOn && !locked;
        Ui.enable(sleepFrom, bed);
        Ui.enable(sleepTo, bed);
        sleepDays.setEnabled(bed);
        Ui.enable(graySwitch, bed);
        Ui.enable(dndSwitch, bed);
        boolean sch = schedOn && !locked;
        Ui.enable(schedFrom, sch);
        Ui.enable(schedTo, sch);
        schedDays.setEnabled(sch);
    }

    /** which: 0 bedtime start, 1 bedtime end, 2 schedule start, 3 schedule end */
    private void pickTime(int which) {
        int cur = which == 0 ? sleepStart : which == 1 ? sleepEnd : which == 2 ? schedStart : schedEnd;
        new TimePickerDialog(this, (view, h, m) -> {
            int v = h * 60 + m;
            if (which == 0) sleepStart = v;
            else if (which == 1) sleepEnd = v;
            else if (which == 2) schedStart = v;
            else schedEnd = v;
            refreshEnabled();
        }, cur / 60, cur % 60, android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    private void askDndAccess() {
        new AlertDialog.Builder(this)
                .setTitle("Allow Do Not Disturb")
                .setMessage("On the next screen, find Focus Guard and allow it, so it can turn on Do Not "
                        + "Disturb at bedtime.")
                .setPositiveButton("Open settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                .setNegativeButton("Later", null)
                .show();
    }

    // ---------- app timers ----------

    private void refreshLimits() {
        limitsBox.removeAllViews();
        if (limits.isEmpty()) {
            limitsBox.addView(Ui.text(this, "No app timers yet.", 14, Ui.MUTED, false));
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

    // ---------- save ----------

    private void writeAll() {
        Prefs.setLimits(this, limits);
        Prefs.setSleep(this, sleepOn, sleepStart, sleepEnd);
        Prefs.setSleepDays(this, sleepDays.getDays());
        Prefs.putBool(this, "gray_sleep", graySwitch.isChecked());
        Prefs.putBool(this, "dnd_sleep", dndSwitch.isChecked());
        Prefs.setSchedule(this, schedOn, schedStart, schedEnd, schedDays.getDays());
    }

    private void confirmSave() {
        if (locked) return;
        if (limits.isEmpty() && !sleepOn && !schedOn) {
            writeAll();
            Prefs.putLong(this, "plan_lock", 0L);
            Notifier.update(this, true);
            Rules.toast(this, "Plan cleared.");
            finish();
            return;
        }
        if ((sleepOn && sleepStart == sleepEnd) || (schedOn && schedStart == schedEnd)) {
            Rules.toast(this, "Start and end time can't be the same.");
            return;
        }
        if ((sleepOn && sleepDays.getDays() == 0) || (schedOn && schedDays.getDays() == 0)) {
            Rules.toast(this, "Pick at least one day.");
            return;
        }
        long until = System.currentTimeMillis() + Rules.WEEK;
        new AlertDialog.Builder(this)
                .setTitle("Lock this plan for 7 days?")
                .setMessage("Until " + Prefs.dateTime(this, until) + " you won't be able to change your app "
                        + "timers, bedtime or focus schedule, remove apps from your distracting list, or "
                        + "switch Focus Guard off. After that, you can keep the plan or change it.")
                .setPositiveButton("Lock it", (d, w) -> {
                    writeAll();
                    Rules.lockPlan(this);
                    Notifier.update(this, true);
                    Rules.toast(this, "Plan saved and locked for 7 days.");
                    finish();
                })
                .setNegativeButton("Not yet", null)
                .show();
    }
}
