package com.usama.focusguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {

    private static final int HOME = 0, DASH = 1, FOCUS = 2, SETTINGS = 3;
    private static final String[] TAB_NAMES = {"Home", "Dashboard", "Focus", "Settings"};
    private static final String[] TAB_ICONS = {"🏠", "📊", "🎯", "⚙️"};
    private static final int[] PRESETS = {15, 25, 45, 60};
    private static final String NOTIF_PERM = "android.permission.POST_NOTIFICATIONS";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScrollView[] tabs = new ScrollView[4];
    private final TextView[] navIcons = new TextView[4];
    private final TextView[] navLabels = new TextView[4];
    private int[] ringColors;
    private int tab;
    private int ticks;

    private LinearLayout accCard;

    // Usage data shared by Home and Dashboard
    private UsageData.Day[] week;
    private final Map<String, Drawable> icons = new ConcurrentHashMap<>();
    private final Map<String, String> labels = new ConcurrentHashMap<>();
    private boolean loading;

    // Home
    private LinearLayout weekCard;
    private LinearLayout homeNoAccess;
    private LinearLayout homeSummary;
    private RingView homeRing;
    private LinearLayout homeLegend;
    private TextView homeUnlocks;
    private LinearLayout homeRows;

    // Dashboard
    private LinearLayout dashNoAccess;
    private LinearLayout dashContent;
    private final List<Button> metricChips = new ArrayList<>();
    private int metric;          // 0 screen time, 1 times opened, 2 unlocks
    private int dayIdx = 6;      // 6 = today
    private TextView dayLabel;
    private Button prevDay;
    private Button nextDay;
    private RingView ring;
    private LinearLayout legend;
    private BarView weekBars;
    private TextView appsTitle;
    private LinearLayout appList;

    // Focus
    private FocusDial dial;
    private final List<Button> presetChips = new ArrayList<>();
    private Button minusBtn;
    private Button plusBtn;
    private Button startBtn;
    private TextView focusNote;
    private LinearLayout distractBox;
    private Switch grayFocus;
    private LinearLayout focusRows;

    // Settings
    private LinearLayout settingsBody;

    private AlertDialog weekDialog;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshFocus();
            refreshTop();
            if (++ticks % 15 == 0) {
                refreshHomeRows();
                refreshSettings();
            }
            handler.postDelayed(this, 1000L);
        }
    };

    // =====================================================================
    // Setup
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyTheme(this);
        super.onCreate(savedInstanceState);
        ringColors = new int[]{
                Color.parseColor(Ui.dark ? "#7FC7B0" : "#1F3A33"), Color.parseColor("#F2C14E"),
                Color.parseColor("#5E9C8A"), Color.parseColor("#D9825B"), Color.parseColor("#8C7AA9"),
                Color.parseColor(Ui.dark ? "#5A6B65" : "#C9D6CF")
        };
        getWindow().setStatusBarColor(Color.parseColor(Ui.PAPER));
        if (!Ui.dark && Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(Ui.PAPER));

        accCard = Ui.card(this, Ui.WARN);
        accCard.addView(Ui.text(this, "⚠️ The blocker is switched off", 15, Ui.INK, true));
        accCard.addView(Ui.text(this, "Nothing can be blocked until you turn on Focus Guard in Accessibility.",
                13, Ui.INK, false), Ui.top(this, 2));
        Button fix = Ui.button(this, "Turn it on", false);
        fix.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        accCard.addView(fix, Ui.top(this, 8));
        LinearLayout.LayoutParams accLp = Ui.top(this, 0);
        accLp.setMargins(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), 0);
        root.addView(accCard, accLp);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        for (int i = 0; i < 4; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setFillViewport(true);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 28));
            sv.addView(col);
            content.addView(sv);
            tabs[i] = sv;
        }
        buildHome(col(HOME));
        buildDashboard(col(DASH));
        buildFocus(col(FOCUS));
        buildSettings(col(SETTINGS));

        root.addView(buildNav());
        setContentView(root);
        openTab(Prefs.sp(this).getInt("tab", HOME));
    }

    private LinearLayout col(int i) { return (LinearLayout) tabs[i].getChildAt(0); }

    private LinearLayout buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.parseColor(Ui.CARD));
        nav.setElevation(Ui.dp(this, 10));
        nav.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            navIcons[i] = Ui.text(this, TAB_ICONS[i], 19, Ui.INK, false);
            navIcons[i].setGravity(Gravity.CENTER);
            navIcons[i].setPadding(Ui.dp(this, 18), Ui.dp(this, 3), Ui.dp(this, 18), Ui.dp(this, 3));
            navLabels[i] = Ui.text(this, TAB_NAMES[i], 12, Ui.MUTED, false);
            navLabels[i].setPadding(0, Ui.dp(this, 3), 0, 0);
            item.addView(navIcons[i]);
            item.addView(navLabels[i]);
            item.setOnClickListener(v -> openTab(idx));
            nav.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return nav;
    }

    private void openTab(int i) {
        tab = Math.max(0, Math.min(3, i));
        Prefs.sp(this).edit().putInt("tab", tab).apply();
        for (int k = 0; k < 4; k++) {
            boolean on = k == tab;
            tabs[k].setVisibility(on ? View.VISIBLE : View.GONE);
            navIcons[k].setBackground(on ? Ui.round(this, Ui.dark ? "#3A4A3F" : "#F7E3A8", 16) : null);
            navLabels[k].setTextColor(Color.parseColor(on ? Ui.INK : Ui.MUTED));
            navLabels[k].setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (tab == HOME || tab == DASH) loadUsage();
    }

    private TextView title(LinearLayout col, String text, String sub) {
        col.addView(Ui.text(this, text, 30, Ui.INK, true));
        TextView s = Ui.text(this, sub, 14, Ui.MUTED, false);
        col.addView(s, Ui.top(this, 2));
        return s;
    }

    // =====================================================================
    // Home
    // =====================================================================

    private void buildHome(LinearLayout col) {
        title(col, "Focus Guard",
                new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()));

        weekCard = Ui.card(this, Ui.WARN);
        weekCard.addView(Ui.text(this, "Your week has ended", 16, Ui.INK, true));
        weekCard.addView(Ui.text(this, "Your app timers, bedtime and focus schedule are paused. Continue with "
                + "last week's settings, or set new restrictions?", 14, Ui.INK, false), Ui.top(this, 6));
        LinearLayout wr = Ui.row(this);
        Button keep = Ui.button(this, "Keep previous", false);
        keep.setOnClickListener(v -> keepPreviousWeek());
        Button change = Ui.button(this, "Set new", false);
        change.setOnClickListener(v -> openPlan(null));
        wr.addView(keep, Ui.weight(this, 1f, 0));
        wr.addView(change, Ui.weight(this, 1f, 8));
        weekCard.addView(wr, Ui.top(this, 12));
        col.addView(weekCard, Ui.top(this, 16));

        // Today's screen time
        homeNoAccess = usagePrompt();
        col.addView(homeNoAccess, Ui.top(this, 16));

        homeSummary = Ui.card(this, Ui.CARD);
        homeSummary.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 14));
        homeRing = new RingView(this);
        homeSummary.addView(homeRing);
        homeLegend = new LinearLayout(this);
        homeLegend.setOrientation(LinearLayout.VERTICAL);
        homeSummary.addView(homeLegend, Ui.top(this, 12));
        LinearLayout foot = Ui.row(this);
        homeUnlocks = Ui.text(this, "", 14, Ui.MUTED, false);
        foot.addView(homeUnlocks, Ui.weight(this, 1f, 0));
        foot.addView(Ui.text(this, "Dashboard ›", 14, Ui.INK, true));
        homeSummary.addView(foot, Ui.top(this, 12));
        homeSummary.setOnClickListener(v -> openTab(DASH));
        col.addView(homeSummary, Ui.top(this, 16));

        homeRows = new LinearLayout(this);
        homeRows.setOrientation(LinearLayout.VERTICAL);
        col.addView(homeRows, Ui.top(this, 0));
    }

    private LinearLayout usagePrompt() {
        LinearLayout c = Ui.card(this, Ui.CARD);
        c.addView(Ui.text(this, "📊 See your screen time", 17, Ui.INK, true));
        c.addView(Ui.text(this, "Allow usage access so Focus Guard can show how long you spend on each app. "
                + "On the next screen, find Focus Guard and turn it on.", 14, Ui.MUTED, false), Ui.top(this, 4));
        Button allow = Ui.button(this, "Allow usage access", false);
        allow.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        c.addView(allow, Ui.top(this, 12));
        return c;
    }

    private void refreshHomeRows() {
        homeRows.removeAllViews();
        boolean locked = Rules.planLocked(this);

        // Ways to disconnect
        homeRows.addView(Ui.section(this, "Ways to disconnect"), Ui.top(this, 22));
        LinearLayout ways = Ui.listCard(this);

        Map<String, Integer> limits = Prefs.getLimits(this);
        String timersSub;
        if (limits.isEmpty()) {
            timersSub = "No timers";
        } else {
            int up = 0;
            for (Map.Entry<String, Integer> e : limits.entrySet()) {
                if (Prefs.getUsage(this, e.getKey()) >= e.getValue() * Rules.MIN) up++;
            }
            timersSub = limits.size() + (limits.size() == 1 ? " app timer" : " app timers")
                    + (!locked ? " · paused" : up > 0 ? " · " + up + " used up today" : "");
        }
        ways.addView(Ui.listRow(this, "⏳", "App timers", timersSub, v -> openPlan("timers")));
        ways.addView(Ui.divider(this));

        String bedSub = !Prefs.sleepOn(this) ? "Off"
                : Prefs.clock(this, Prefs.sleepStart(this)) + " – " + Prefs.clock(this, Prefs.sleepEnd(this))
                + " · " + Prefs.days(Prefs.sleepDays(this)) + (!locked ? " · paused" : "");
        if (Rules.sleepNow(this)) bedSub = "On now · until " + Prefs.clock(this, Prefs.sleepEnd(this));
        ways.addView(Ui.listRow(this, "🌙", "Bedtime mode", bedSub, v -> openPlan("bedtime")));
        ways.addView(Ui.divider(this));

        String focusSub = Rules.focusActive(this)
                ? "On · " + Prefs.format(Rules.focusLeft(this)) + " left"
                : Rules.blockedApps(this).size() + " distracting apps · tap to start";
        ways.addView(Ui.listRow(this, "🎯", "Focus mode", focusSub, v -> openTab(FOCUS)));
        ways.addView(Ui.divider(this));

        String schSub = !Prefs.schedOn(this) ? "Off"
                : Prefs.clock(this, Prefs.schedStart(this)) + " – " + Prefs.clock(this, Prefs.schedEnd(this))
                + " · " + Prefs.days(Prefs.schedDays(this)) + (!locked ? " · paused" : "");
        ways.addView(Ui.listRow(this, "🗓️", "Focus schedule", schSub, v -> openPlan("schedule")));
        homeRows.addView(ways, Ui.top(this, 8));

        // Weekly plan status
        String lockTxt = !Rules.hasPlan(this) ? "Set timers, bedtime and a schedule, then lock them for 7 days."
                : locked ? "Locked until " + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock"))
                : "Week ended · choose to keep or change";
        LinearLayout lockCard = Ui.listCard(this);
        lockCard.addView(Ui.listRow(this, locked ? "🔒" : "🔓", "Weekly plan", lockTxt, v -> openPlan(null)));
        homeRows.addView(lockCard, Ui.top(this, 12));

        // Reduce interruptions
        homeRows.addView(Ui.section(this, "Reduce interruptions"), Ui.top(this, 22));
        LinearLayout red = Ui.listCard(this);
        red.addView(Ui.listRow(this, "🔔", "Manage notifications", "Choose which apps can notify you",
                v -> openSystem("android.settings.ALL_APPS_NOTIFICATION_SETTINGS")));
        red.addView(Ui.divider(this));
        String dndSub = Dnd.wanted(this) ? "Turns on at bedtime" : "Silence your phone";
        red.addView(Ui.listRow(this, "🔕", "Do Not Disturb", dndSub,
                v -> openSystem("android.settings.ZEN_MODE_SETTINGS")));
        homeRows.addView(red, Ui.top(this, 8));
    }

    private void openPlan(String section) {
        Intent i = new Intent(this, PlanActivity.class);
        if (section != null) i.putExtra("section", section);
        startActivity(i);
    }

    private void openSystem(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void keepPreviousWeek() {
        Rules.lockPlan(this);
        Notifier.update(this, true);
        Rules.toast(this, "Last week's settings are locked in until "
                + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock")) + ".");
        refreshTop();
        refreshHomeRows();
    }

    private void askAboutNewWeek() {
        if (!Rules.weekOver(this) || (weekDialog != null && weekDialog.isShowing())) return;
        weekDialog = new AlertDialog.Builder(this)
                .setTitle("Your week has ended")
                .setMessage("Your app timers, bedtime and focus schedule are paused. Do you want to continue "
                        + "with last week's settings, or set new restrictions for this week?")
                .setPositiveButton("Keep previous", (d, w) -> keepPreviousWeek())
                .setNegativeButton("Set new", (d, w) -> openPlan(null))
                .setNeutralButton("Later", null)
                .show();
    }

    // =====================================================================
    // Usage data (Home summary + Dashboard)
    // =====================================================================

    private void loadUsage() {
        boolean access = UsageData.hasAccess(this);
        homeNoAccess.setVisibility(access ? View.GONE : View.VISIBLE);
        homeSummary.setVisibility(access ? View.VISIBLE : View.GONE);
        dashNoAccess.setVisibility(access ? View.GONE : View.VISIBLE);
        dashContent.setVisibility(access ? View.VISIBLE : View.GONE);
        if (!access || loading) return;
        loading = true;
        new Thread(() -> {
            Set<String> ex = UsageData.excluded(this);
            UsageData.Day[] w = new UsageData.Day[7];
            for (int i = 0; i < 7; i++) w[i] = UsageData.day(this, 6 - i, ex);
            PackageManager pm = getPackageManager();
            for (UsageData.Day d : w) {
                for (String p : d.perApp.keySet()) {
                    if (labels.containsKey(p)) continue;
                    labels.put(p, Apps.label(this, p));
                    try {
                        icons.put(p, pm.getApplicationIcon(p));
                    } catch (Exception ignored) {
                    }
                }
            }
            runOnUiThread(() -> {
                loading = false;
                week = w;
                showHomeSummary();
                showDay();
            });
        }).start();
    }

    private String name(String pkg) {
        String n = labels.get(pkg);
        return n == null ? Apps.label(this, pkg) : n;
    }

    private List<Map.Entry<String, Long>> sortedApps(UsageData.Day d) {
        List<Map.Entry<String, Long>> s = new ArrayList<>(d.perApp.entrySet());
        s.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return s;
    }

    private void showHomeSummary() {
        if (week == null) return;
        UsageData.Day d = week[6];
        List<Map.Entry<String, Long>> sorted = sortedApps(d);
        int top = Math.min(3, sorted.size());
        long other = 0;
        for (int i = top; i < sorted.size(); i++) other += sorted.get(i).getValue();
        long[] vals = new long[top + (other > 0 ? 1 : 0)];
        int[] cols = new int[vals.length];
        homeLegend.removeAllViews();
        for (int i = 0; i < top; i++) {
            vals[i] = sorted.get(i).getValue();
            cols[i] = ringColors[i];
            homeLegend.addView(legendRow(cols[i], name(sorted.get(i).getKey()), Prefs.duration(vals[i])),
                    Ui.top(this, 4));
        }
        if (other > 0) {
            vals[top] = other;
            cols[top] = ringColors[5];
            homeLegend.addView(legendRow(cols[top], "Other apps", Prefs.duration(other)), Ui.top(this, 4));
        }
        homeRing.setData(vals, cols, Prefs.duration(d.total), "Today");
        homeUnlocks.setText(d.unlocks < 0 ? "" : "🔓 " + d.unlocks + " unlocks today");
    }

    // =====================================================================
    // Dashboard
    // =====================================================================

    private void buildDashboard(LinearLayout col) {
        title(col, "Dashboard", "How you've used your phone this week.");

        dashNoAccess = usagePrompt();
        col.addView(dashNoAccess, Ui.top(this, 16));

        dashContent = new LinearLayout(this);
        dashContent.setOrientation(LinearLayout.VERTICAL);
        col.addView(dashContent, Ui.top(this, 0));

        // What to show
        LinearLayout metrics = Ui.row(this);
        String[] mNames = {"Screen time", "Times opened", "Unlocks"};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            Button b = Ui.chip(this, mNames[i]);
            b.setOnClickListener(v -> {
                metric = m;
                showDay();
            });
            metrics.addView(b, Ui.weight(this, 1f, i == 0 ? 0 : 6));
            metricChips.add(b);
        }
        dashContent.addView(metrics, Ui.top(this, 16));

        // Day switcher
        LinearLayout dayRow = Ui.row(this);
        prevDay = Ui.chip(this, "‹");
        nextDay = Ui.chip(this, "›");
        prevDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        nextDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        prevDay.setOnClickListener(v -> pickDay(dayIdx - 1));
        nextDay.setOnClickListener(v -> pickDay(dayIdx + 1));
        dayLabel = Ui.text(this, "Today", 17, Ui.INK, true);
        dayLabel.setGravity(Gravity.CENTER);
        dayRow.addView(prevDay, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 40)));
        dayRow.addView(dayLabel, Ui.weight(this, 1f, 8));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 40));
        nlp.setMarginStart(Ui.dp(this, 8));
        dayRow.addView(nextDay, nlp);
        dashContent.addView(dayRow, Ui.top(this, 14));

        LinearLayout ringCard = Ui.card(this, Ui.CARD);
        ring = new RingView(this);
        ringCard.addView(ring);
        legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        ringCard.addView(legend, Ui.top(this, 12));
        dashContent.addView(ringCard, Ui.top(this, 10));

        LinearLayout wk = Ui.card(this, Ui.CARD);
        wk.addView(Ui.text(this, "Last 7 days", 17, Ui.INK, true));
        wk.addView(Ui.text(this, "Tap a day to see it.", 13, Ui.MUTED, false), Ui.top(this, 2));
        weekBars = new BarView(this, 170);
        weekBars.setOnPick(this::pickDay);
        wk.addView(weekBars, Ui.top(this, 8));
        dashContent.addView(wk, Ui.top(this, 12));

        LinearLayout apps = Ui.card(this, Ui.CARD);
        appsTitle = Ui.text(this, "Apps", 17, Ui.INK, true);
        apps.addView(appsTitle);
        apps.addView(Ui.text(this, "Tap an app for its hourly usage and timer.", 13, Ui.MUTED, false),
                Ui.top(this, 2));
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        apps.addView(appList, Ui.top(this, 6));
        dashContent.addView(apps, Ui.top(this, 12));
    }

    private void pickDay(int i) {
        if (week == null) return;
        dayIdx = Math.max(0, Math.min(6, i));
        showDay();
    }

    private long opens(UsageData.Day d, String pkg) {
        Integer o = d.opens.get(pkg);
        return o == null ? 0 : o;
    }

    private long totalOpens(UsageData.Day d) {
        long t = 0;
        for (Integer o : d.opens.values()) t += o;
        return t;
    }

    private void showDay() {
        for (int i = 0; i < metricChips.size(); i++) Ui.setChip(this, metricChips.get(i), i == metric);
        if (week == null) return;
        final UsageData.Day d = week[dayIdx];
        String dayName = dayIdx == 6 ? "Today" : dayIdx == 5 ? "Yesterday"
                : new SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(new Date(d.start));
        dayLabel.setText(dayName);
        Ui.enable(prevDay, dayIdx > 0);
        Ui.enable(nextDay, dayIdx < 6);

        // Apps sorted by the chosen measure
        List<Map.Entry<String, Long>> sorted = new ArrayList<>();
        for (String p : d.perApp.keySet()) {
            long v = metric == 1 ? opens(d, p) : d.perApp.get(p);
            sorted.add(new java.util.AbstractMap.SimpleEntry<>(p, v));
        }
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        // Ring
        legend.removeAllViews();
        if (metric == 2) {
            long u = Math.max(0, d.unlocks);
            ring.setData(new long[]{u > 0 ? 1 : 0}, new int[]{ringColors[1]},
                    d.unlocks < 0 ? "–" : String.valueOf(u), "unlocks · " + dayName.toLowerCase(Locale.getDefault()));
        } else {
            int top = Math.min(5, sorted.size());
            long other = 0;
            for (int i = top; i < sorted.size(); i++) other += sorted.get(i).getValue();
            long[] vals = new long[top + (other > 0 ? 1 : 0)];
            int[] cols = new int[vals.length];
            for (int i = 0; i < top; i++) {
                vals[i] = sorted.get(i).getValue();
                cols[i] = ringColors[i];
                legend.addView(legendRow(cols[i], name(sorted.get(i).getKey()), fmt(vals[i])), Ui.top(this, 4));
            }
            if (other > 0) {
                vals[top] = other;
                cols[top] = ringColors[5];
                legend.addView(legendRow(cols[top], "Other apps", fmt(other)), Ui.top(this, 4));
            }
            String center = metric == 0 ? Prefs.duration(d.total) : String.valueOf(totalOpens(d));
            String sub = metric == 0 ? dayName : "times opened · " + dayName.toLowerCase(Locale.getDefault());
            ring.setData(vals, cols, center, sub);
        }

        // Week bars
        long[] wv = new long[7];
        String[] wl = new String[7];
        SimpleDateFormat f = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            wv[i] = metric == 0 ? week[i].total : metric == 1 ? totalOpens(week[i]) : Math.max(0, week[i].unlocks);
            wl[i] = i == 6 ? "Today" : f.format(new Date(week[i].start));
        }
        weekBars.setFormat(metric == 0 ? Prefs::duration : v -> String.valueOf(v));
        weekBars.setData(wv, wl, dayIdx);

        // App list
        appsTitle.setText(metric == 1 ? "Apps by times opened" : "Apps by screen time");
        appList.removeAllViews();
        if (sorted.isEmpty()) {
            appList.addView(Ui.text(this, "Nothing recorded for this day.", 14, Ui.MUTED, false), Ui.top(this, 6));
        }
        long max = sorted.isEmpty() ? 1 : Math.max(1, sorted.get(0).getValue());
        Map<String, Integer> limits = Rules.activeLimits(this);
        for (Map.Entry<String, Long> e : sorted) {
            appList.addView(appRow(e.getKey(), e.getValue(), max, limits.get(e.getKey())));
        }
    }

    private String fmt(long v) {
        return metric == 0 ? Prefs.duration(v) : v + (v == 1 ? " time" : " times");
    }

    private View legendRow(int color, String label, String value) {
        LinearLayout row = Ui.row(this);
        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        dot.setBackground(g);
        row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12)));
        row.addView(Ui.text(this, label, 15, Ui.INK, false), Ui.weight(this, 1f, 10));
        row.addView(Ui.text(this, value, 15, Ui.MUTED, false));
        return row;
    }

    private View appRow(String pkg, long value, long max, Integer limit) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
        ImageView icon = new ImageView(this);
        Drawable d = icons.get(pkg);
        if (d != null) icon.setImageDrawable(d);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, name(pkg), 16, Ui.INK, false), Ui.weight(this, 1f, 0));
        top.addView(Ui.text(this, fmt(value), 14, Ui.MUTED, false));
        mid.addView(top);
        mid.addView(progressBar(value / (float) max, "#5E9C8A"), Ui.top(this, 6));
        if (limit != null) {
            mid.addView(Ui.text(this, "⏳ Timer: " + limit + " min a day", 12, Ui.MUTED, false), Ui.top(this, 4));
        }
        row.addView(mid, Ui.weight(this, 1f, 12));
        row.setOnClickListener(v -> startActivity(new Intent(this, AppDetailActivity.class).putExtra("pkg", pkg)));
        return row;
    }

    private View progressBar(float frac, String color) {
        frac = Math.max(0.02f, Math.min(1f, frac));
        LinearLayout track = new LinearLayout(this);
        track.setBackground(Ui.round(this, Ui.TRACK, 4));
        View fill = new View(this);
        fill.setBackground(Ui.round(this, color, 4));
        track.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(this, 6), frac));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 6), 1f - frac + 0.0001f));
        return track;
    }

    // =====================================================================
    // Focus
    // =====================================================================

    private void buildFocus(LinearLayout col) {
        title(col, "Focus mode", "Pause your distracting apps while you study.");

        LinearLayout card = Ui.card(this, Ui.CARD);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 18), Ui.dp(this, 18));
        dial = new FocusDial(this);
        dial.setOnClickListener(v -> askCustomMinutes());
        card.addView(dial);

        LinearLayout adjust = Ui.row(this);
        minusBtn = roundBtn("−");
        minusBtn.setOnClickListener(v -> setMinutes(Prefs.getMinutes(this) - 5));
        plusBtn = roundBtn("+");
        plusBtn.setOnClickListener(v -> setMinutes(Prefs.getMinutes(this) + 5));
        adjust.addView(minusBtn, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        for (int i = 0; i < PRESETS.length; i++) {
            final int p = PRESETS[i];
            Button b = Ui.chip(this, String.valueOf(p));
            b.setOnClickListener(v -> setMinutes(p));
            adjust.addView(b, Ui.weight(this, 1f, 6));
            presetChips.add(b);
        }
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        plp.setMarginStart(Ui.dp(this, 6));
        adjust.addView(plusBtn, plp);
        card.addView(adjust, Ui.top(this, 18));

        startBtn = Ui.button(this, "Start focus", true);
        startBtn.setOnClickListener(v -> confirmStart());
        card.addView(startBtn, Ui.top(this, 16));
        focusNote = Ui.text(this, "", 13, Ui.MUTED, false);
        focusNote.setGravity(Gravity.CENTER);
        card.addView(focusNote, Ui.top(this, 10));
        col.addView(card, Ui.top(this, 16));

        // Distracting apps
        col.addView(Ui.section(this, "Distracting apps"), Ui.top(this, 22));
        LinearLayout dCard = Ui.card(this, Ui.CARD);
        distractBox = new LinearLayout(this);
        distractBox.setOrientation(LinearLayout.VERTICAL);
        dCard.addView(distractBox);
        dCard.setOnClickListener(v -> startActivity(new Intent(this, BlockListActivity.class)));
        col.addView(dCard, Ui.top(this, 8));

        // Options
        col.addView(Ui.section(this, "Options"), Ui.top(this, 22));
        LinearLayout opts = Ui.listCard(this);
        LinearLayout grayRow = Ui.listRow(this, "◐", "Grayscale distracting apps",
                "If you open one during focus, the screen turns gray", null);
        grayFocus = new Switch(this);
        grayFocus.setChecked(Gray.focusWanted(this));
        grayFocus.setOnCheckedChangeListener((b, on) -> {
            Prefs.putBool(this, "gray_focus", on);
            if (on && !Gray.canControl(this)) {
                Rules.toast(this, "Grayscale needs a one-time setup. See the Settings tab.");
            }
        });
        grayRow.addView(grayFocus);
        opts.addView(grayRow);
        opts.addView(Ui.divider(this));
        focusRows = new LinearLayout(this);
        focusRows.setOrientation(LinearLayout.VERTICAL);
        opts.addView(focusRows);
        col.addView(opts, Ui.top(this, 8));

        LinearLayout tips = Ui.card(this, Ui.CARD);
        tips.addView(Ui.text(this, "Start focus without opening the app", 15, Ui.INK, true));
        tips.addView(Ui.text(this,
                "• Notification bar: tap 30, 45 or 60 min on the Focus Guard notification.\n"
                        + "• Quick Settings: swipe down twice, tap ✏️, and add the \"Focus\" tile.\n"
                        + "• Home screen: long-press the Focus Guard icon.",
                13, Ui.MUTED, false), Ui.top(this, 6));
        col.addView(tips, Ui.top(this, 12));
    }

    private Button roundBtn(String s) {
        Button b = Ui.chip(this, s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(Ui.round(this, Ui.PAPER, 22));
        return b;
    }

    private void setMinutes(int m) {
        if (Rules.focusActive(this)) return;
        Prefs.setMinutes(this, Math.max(5, Math.min(720, m)));
        refreshFocus();
    }

    private void askCustomMinutes() {
        if (Rules.focusActive(this)) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.CENTER);
        input.setText(String.valueOf(Prefs.getMinutes(this)));
        input.setSelectAllOnFocus(true);
        Ui.styleInput(this, input);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        wrap.addView(input, Ui.top(this, 0));
        new AlertDialog.Builder(this)
                .setTitle("Focus for how many minutes?")
                .setView(wrap)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        int m = Integer.parseInt(input.getText().toString().trim());
                        if (m >= 1 && m <= 720) {
                            Prefs.setMinutes(this, m);
                            refreshFocus();
                        } else {
                            Rules.toast(this, "Choose between 1 and 720 minutes.");
                        }
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        input.requestFocus();
    }

    private void refreshFocus() {
        boolean on = Rules.focusActive(this);
        int mins = Prefs.getMinutes(this);
        if (Rules.emergencyOn(this) && on) {
            dial.setState(Rules.focusProgress(this), true,
                    Prefs.format(Rules.focusLeft(this)), "emergency time on");
        } else if (on) {
            dial.setState(Rules.focusProgress(this), true, Prefs.format(Rules.focusLeft(this)),
                    "until " + android.text.format.DateFormat.getTimeFormat(this)
                            .format(new Date(Rules.focusEndsAt(this))));
        } else {
            dial.setState(1f, false, mins >= 60 && mins % 60 == 0 ? (mins / 60) + " h" : mins + " min",
                    "tap to type a time");
        }
        startBtn.setText(on ? "Focus is on" : "Start focus");
        Ui.enable(startBtn, !on);
        Ui.enable(minusBtn, !on);
        Ui.enable(plusBtn, !on);
        Ui.enable(grayFocus, !on);
        for (int i = 0; i < presetChips.size(); i++) {
            Ui.setChip(this, presetChips.get(i), mins == PRESETS[i]);
            Ui.enable(presetChips.get(i), !on);
        }
        if (on) {
            focusNote.setText(Rules.scheduleNow(this) && !Rules.focusOn(this)
                    ? "Your focus schedule is running. 2 emergency minutes every 20 minutes."
                    : "Distracting apps, websites and settings are locked. 2 emergency minutes every 20 minutes.");
        } else if (Rules.sleepNow(this)) {
            focusNote.setText("🌙 Bedtime is on until " + Prefs.clock(this, Prefs.sleepEnd(this)) + ".");
        } else {
            focusNote.setText("Once started, focus can't be stopped until the time is up.");
        }
    }

    private void refreshFocusLists() {
        // Distracting apps preview
        distractBox.removeAllViews();
        LinearLayout head = Ui.row(this);
        Set<String> apps = Rules.blockedApps(this);
        Set<String> sites = Rules.blockedSites(this);
        head.addView(Ui.text(this, apps.size() + (apps.size() == 1 ? " app" : " apps") + " · "
                + sites.size() + (sites.size() == 1 ? " website" : " websites"), 16, Ui.INK, true),
                Ui.weight(this, 1f, 0));
        head.addView(Ui.text(this, "Edit ›", 14, Ui.INK, true));
        distractBox.addView(head);
        if (apps.isEmpty()) {
            distractBox.addView(Ui.text(this, "Choose the apps and websites that distract you.", 13,
                    Ui.MUTED, false), Ui.top(this, 4));
        } else {
            LinearLayout iconsRow = Ui.row(this);
            int shown = 0;
            for (String p : apps) {
                if (shown == 7) break;
                ImageView iv = new ImageView(this);
                try {
                    iv.setImageDrawable(getPackageManager().getApplicationIcon(p));
                } catch (Exception e) {
                    continue;
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
                lp.setMarginEnd(Ui.dp(this, 8));
                iconsRow.addView(iv, lp);
                shown++;
            }
            if (apps.size() > shown) {
                iconsRow.addView(Ui.text(this, "+" + (apps.size() - shown), 14, Ui.MUTED, true));
            }
            distractBox.addView(iconsRow, Ui.top(this, 10));
        }

        // Schedule row
        focusRows.removeAllViews();
        String schSub = !Prefs.schedOn(this) ? "Off · turn focus on automatically"
                : Prefs.clock(this, Prefs.schedStart(this)) + " – " + Prefs.clock(this, Prefs.schedEnd(this))
                + " · " + Prefs.days(Prefs.schedDays(this));
        focusRows.addView(Ui.listRow(this, "🗓️", "Focus schedule", schSub, v -> openPlan("schedule")));
    }

    private void confirmStart() {
        if (Rules.focusActive(this)) return;
        final int mins = Prefs.getMinutes(this);
        new AlertDialog.Builder(this)
                .setTitle("Start a " + mins + "-minute focus?")
                .setMessage("Until it ends you won't be able to open your distracting apps or websites, "
                        + "change your list, or switch Focus Guard off. You'll get 2 emergency minutes "
                        + "every 20 minutes.")
                .setPositiveButton("Start", (d, w) -> {
                    Rules.startFocus(this, mins);
                    refreshFocus();
                    refreshHomeRows();
                })
                .setNegativeButton("Not yet", null)
                .show();
    }

    // =====================================================================
    // Settings
    // =====================================================================

    private void buildSettings(LinearLayout col) {
        title(col, "Settings", "Permissions, appearance and updates.");
        settingsBody = new LinearLayout(this);
        settingsBody.setOrientation(LinearLayout.VERTICAL);
        col.addView(settingsBody, Ui.top(this, 0));
    }

    private void refreshSettings() {
        settingsBody.removeAllViews();

        settingsBody.addView(Ui.section(this, "Appearance"), Ui.top(this, 20));
        LinearLayout look = Ui.card(this, Ui.CARD);
        LinearLayout themeRow = Ui.row(this);
        String[] names = {"Phone setting", "Light", "Dark"};
        for (int i = 0; i < 3; i++) {
            final int choice = i;
            Button b = Ui.chip(this, names[i]);
            Ui.setChip(this, b, Ui.themeChoice(this) == i);
            b.setOnClickListener(v -> {
                Prefs.sp(this).edit().putInt("theme", choice).commit();
                recreate();
            });
            themeRow.addView(b, Ui.weight(this, 1f, i == 0 ? 0 : 6));
        }
        look.addView(themeRow);
        settingsBody.addView(look, Ui.top(this, 8));

        settingsBody.addView(Ui.section(this, "Permissions"), Ui.top(this, 22));
        LinearLayout perms = Ui.listCard(this);
        permRow(perms, "Blocker (Accessibility)", "Blocks apps and websites", Rules.blockerOn(this),
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        perms.addView(Ui.divider(this));
        permRow(perms, "Usage access", "Screen time on the dashboard", UsageData.hasAccess(this),
                () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        perms.addView(Ui.divider(this));
        permRow(perms, "Notifications", "Start focus from the notification bar", !needsNotifications(),
                this::askNotifications);
        perms.addView(Ui.divider(this));
        permRow(perms, "Do Not Disturb", "Silence the phone at bedtime", Dnd.canControl(this),
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        perms.addView(Ui.divider(this));
        permRow(perms, "Grayscale", "Gray screen at bedtime and for distracting apps", Gray.canControl(this), null);
        settingsBody.addView(perms, Ui.top(this, 8));

        LinearLayout help = Ui.card(this, Ui.CARD);
        help.addView(Ui.text(this, "If the Accessibility switch is greyed out: open App info, tap ⋮ (top right), "
                + "then \"Allow restricted settings\", and try again.", 13, Ui.MUTED, false));
        Button info = Ui.button(this, "Open App info", false);
        info.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName()))));
        help.addView(info, Ui.top(this, 10));
        settingsBody.addView(help, Ui.top(this, 10));

        if (!Gray.canControl(this)) {
            LinearLayout gray = Ui.card(this, Ui.WARN);
            gray.addView(Ui.text(this, "One-time setup for grayscale", 16, Ui.INK, true));
            gray.addView(Ui.text(this, "Android only lets an app turn the screen gray after you allow it once "
                    + "from a computer, using this command:", 14, Ui.INK, false), Ui.top(this, 6));
            TextView cmd = Ui.text(this, Gray.GRANT_COMMAND, 13, Ui.INK, false);
            cmd.setTypeface(Typeface.MONOSPACE);
            cmd.setTextIsSelectable(true);
            cmd.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
            cmd.setBackground(Ui.round(this, Ui.PAPER, 8));
            gray.addView(cmd, Ui.top(this, 8));
            Button copy = Ui.button(this, "Copy command", false);
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("command", Gray.GRANT_COMMAND));
                Rules.toast(this, "Command copied.");
            });
            gray.addView(copy, Ui.top(this, 10));
            settingsBody.addView(gray, Ui.top(this, 12));
        }

        settingsBody.addView(Ui.section(this, "App"), Ui.top(this, 22));
        LinearLayout app = Ui.listCard(this);
        app.addView(Ui.listRow(this, "⬇️", "Check for updates",
                "Installed: build " + Updater.installedBuild(this), v -> Updater.check(this)));
        settingsBody.addView(app, Ui.top(this, 8));
    }

    private void permRow(LinearLayout card, String title, String desc, boolean ok, Runnable action) {
        LinearLayout row = Ui.listRow(this, ok ? "✅" : "⚪", title, desc, null);
        if (!ok && action != null) {
            Button b = Ui.chip(this, "Allow");
            Ui.setChip(this, b, true);
            b.setOnClickListener(v -> action.run());
            row.addView(b);
        }
        card.addView(row);
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    private void refreshTop() {
        accCard.setVisibility(Rules.blockerOn(this) ? View.GONE : View.VISIBLE);
        weekCard.setVisibility(Rules.weekOver(this) ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsNotifications() && !Prefs.getBool(this, "asked_notif")) {
            Prefs.putBool(this, "asked_notif", true);
            requestPermissions(new String[]{NOTIF_PERM}, 7);
        }
        Notifier.update(this, true);
        refreshTop();
        refreshHomeRows();
        refreshFocus();
        refreshFocusLists();
        refreshSettings();
        if (tab == HOME || tab == DASH) loadUsage();
        askAboutNewWeek();
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
        refreshSettings();
    }

    private boolean needsNotifications() {
        return Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(NOTIF_PERM) != PackageManager.PERMISSION_GRANTED;
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (shouldShowRequestPermissionRationale(NOTIF_PERM)) {
            requestPermissions(new String[]{NOTIF_PERM}, 7);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }
}
