package com.usama.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/** Screen time for one app: the last 7 days, and hour by hour for the chosen day. */
public class AppDetailActivity extends Activity {

    private String pkg;
    private UsageData.Day[] week;
    private Set<String> excluded;
    private int selected = 6;   // index 6 = today

    private BarView weekBars;
    private BarView hourBars;
    private TextView dayTitle;
    private TextView dayStats;
    private TextView weekStats;
    private LinearLayout limitCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyTheme(this);
        super.onCreate(savedInstanceState);
        pkg = getIntent().getStringExtra("pkg");
        if (pkg == null) {
            finish();
            return;
        }
        LinearLayout col = Ui.page(this);

        LinearLayout head = Ui.row(this);
        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
        } catch (Exception ignored) {
        }
        head.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        head.addView(Ui.text(this, Apps.label(this, pkg), 24, Ui.INK, true), Ui.weight(this, 1f, 14));
        col.addView(head);

        // Week
        LinearLayout weekCard = Ui.card(this, Ui.CARD);
        weekCard.addView(Ui.text(this, "Last 7 days", 17, Ui.INK, true));
        weekStats = Ui.text(this, "Loading…", 14, Ui.MUTED, false);
        weekCard.addView(weekStats, Ui.top(this, 2));
        weekBars = new BarView(this, 170);
        weekBars.setOnPick(i -> {
            selected = i;
            showDay();
        });
        weekCard.addView(weekBars, Ui.top(this, 10));
        col.addView(weekCard, Ui.top(this, 16));

        // Chosen day
        LinearLayout dayCard = Ui.card(this, Ui.CARD);
        dayTitle = Ui.text(this, "", 17, Ui.INK, true);
        dayCard.addView(dayTitle);
        dayStats = Ui.text(this, "", 14, Ui.MUTED, false);
        dayCard.addView(dayStats, Ui.top(this, 2));
        hourBars = new BarView(this, 140);
        dayCard.addView(hourBars, Ui.top(this, 10));
        col.addView(dayCard, Ui.top(this, 12));

        // Timer
        limitCard = Ui.card(this, Ui.CARD);
        col.addView(limitCard, Ui.top(this, 12));
        showLimit();

        excluded = UsageData.excluded(this);
        new Thread(() -> {
            UsageData.Day[] w = new UsageData.Day[7];
            for (int i = 0; i < 7; i++) w[i] = UsageData.day(this, 6 - i, excluded);
            runOnUiThread(() -> {
                week = w;
                showWeek();
                showDay();
            });
        }).start();
    }

    private long appTime(UsageData.Day d) {
        Long v = d.perApp.get(pkg);
        return v == null ? 0L : v;
    }

    private void showWeek() {
        long[] vals = new long[7];
        String[] labels = new String[7];
        long sum = 0;
        SimpleDateFormat f = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            vals[i] = appTime(week[i]);
            sum += vals[i];
            labels[i] = i == 6 ? "Today" : f.format(new Date(week[i].start));
        }
        weekBars.setData(vals, labels, selected);
        weekStats.setText("Daily average: " + Prefs.duration(sum / 7) + " · Total: " + Prefs.duration(sum));
    }

    private void showDay() {
        if (week == null) return;
        UsageData.Day d = week[selected];
        showWeek();
        String name = selected == 6 ? "Today"
                : selected == 5 ? "Yesterday"
                : new SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(new Date(d.start));
        dayTitle.setText(name + ": " + Prefs.duration(appTime(d)));
        Integer opens = d.opens.get(pkg);
        int o = opens == null ? 0 : opens;
        dayStats.setText("Opened " + o + (o == 1 ? " time" : " times"));
        String[] labels = new String[24];
        labels[0] = "12a";
        labels[6] = "6a";
        labels[12] = "12p";
        labels[18] = "6p";
        hourBars.setData(UsageData.hourly(d, pkg, excluded), labels, -1);
    }

    private void showLimit() {
        limitCard.removeAllViews();
        Integer lim = Prefs.getLimits(this).get(pkg);
        limitCard.addView(Ui.text(this, "⏳ App timer", 17, Ui.INK, true));
        String txt;
        if (lim != null) {
            txt = "Daily limit: " + lim + " min. Used today: "
                    + Prefs.duration(Math.min(Prefs.getUsage(this, pkg), lim * Rules.MIN)) + ".";
            if (Rules.planLocked(this)) {
                txt += "\nLocked until " + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock")) + ".";
            }
        } else {
            txt = "No daily limit for this app.";
        }
        limitCard.addView(Ui.text(this, txt, 14, Ui.MUTED, false), Ui.top(this, 4));
        if (!Rules.planLocked(this)) {
            Button b = Ui.button(this, lim == null ? "Add a timer" : "Change timer", false);
            b.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
            limitCard.addView(b, Ui.top(this, 12));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (limitCard != null) showLimit();
    }
}
