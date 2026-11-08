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

public class MainActivity extends Activity {

    private static final int[] PRESETS = {25, 30, 45, 60, 90};
    private static final String NOTIF_PERM = "android.permission.POST_NOTIFICATIONS";
    private static final String[] TAB_NAMES = {"Dashboard", "Focus", "Limits", "Settings"};
    private static final String[] TAB_ICONS = {"📊", "🎯", "⏳", "⚙️"};
    private static final int[] RING_COLORS = {
            Color.parseColor("#1F3A33"), Color.parseColor("#F2C14E"), Color.parseColor("#5E9C8A"),
            Color.parseColor("#D9825B"), Color.parseColor("#8C7AA9"), Color.parseColor("#C9D6CF")
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> chips = new ArrayList<>();
    private final ScrollView[] tabs = new ScrollView[4];
    private final TextView[] navIcons = new TextView[4];
    private final TextView[] navLabels = new TextView[4];
    private int tab;
    private int ticks;

    // Global
    private LinearLayout accCard;

    // Dashboard
    private LinearLayout usageCard;
    private LinearLayout dashContent;
    private TextView dayLabel;
    private Button prevDay;
    private Button nextDay;
    private RingView ring;
    private LinearLayout legend;
    private TextView statUnlocks;
    private TextView statApps;
    private BarView weekBars;
    private LinearLayout appList;
    private UsageData.Day[] week;
    private final Map<String, Drawable> icons = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> labels = new java.util.concurrent.ConcurrentHashMap<>();
    private int dayIdx = 6;
    private boolean loading;

    // Focus
    private TextView status;
    private TextView statusSub;
    private EditText minutesInput;
    private Button startBtn;
    private Switch grayFocus;
    private TextView listSummary;

    // Limits
    private LinearLayout weekCard;
    private LinearLayout planBody;

    // Settings
    private LinearLayout settingsBody;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            if (++ticks % 15 == 0) {
                refreshPlan();
                refreshSettings();
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor(Ui.INK));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(Ui.PAPER));

        // Blocker-off warning stays above every tab.
        accCard = Ui.card(this, Ui.WARN);
        accCard.addView(Ui.text(this, "⚠️ The blocker is switched off", 15, Ui.INK, true));
        accCard.addView(Ui.text(this, "Nothing can be blocked until you turn on Focus Guard in Accessibility.",
                13, Ui.INK, false), Ui.top(this, 2));
        Button fix = Ui.button(this, "Turn it on", false);
        fix.setOnClickListener(v -> openTab(3));
        accCard.addView(fix, Ui.top(this, 8));
        LinearLayout.LayoutParams accLp = Ui.top(this, 0);
        accLp.setMargins(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), 0);
        root.addView(accCard, accLp);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        for (int i = 0; i < 4; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setFillViewport(true);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 28));
            sv.addView(col);
            content.addView(sv);
            tabs[i] = sv;
        }
        buildDashboard(col(0));
        buildFocus(col(1));
        buildLimits(col(2));
        buildSettings(col(3));

        root.addView(buildNav());
        setContentView(root);
        openTab(Prefs.sp(this).getInt("tab", 0));
    }

    private LinearLayout col(int i) { return (LinearLayout) tabs[i].getChildAt(0); }

    private LinearLayout buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(Ui.dp(this, 8));
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            navIcons[i] = Ui.text(this, TAB_ICONS[i], 20, Ui.INK, false);
            navIcons[i].setGravity(Gravity.CENTER);
            navIcons[i].setPadding(Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 2));
            navLabels[i] = Ui.text(this, TAB_NAMES[i], 12, Ui.MUTED, false);
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
            navIcons[k].setBackground(on ? Ui.round(this, Ui.GOLD, 14) : null);
            navLabels[k].setTextColor(Color.parseColor(on ? Ui.INK : Ui.MUTED));
            navLabels[k].setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (tab == 0) loadDashboard();
    }

    // =====================================================================
    // Dashboard
    // =====================================================================

    private void buildDashboard(LinearLayout col) {
        col.addView(Ui.text(this, "Screen time", 28, Ui.INK, true));

        usageCard = Ui.card(this, Ui.WARN);
        usageCard.addView(Ui.text(this, "Allow usage access", 16, Ui.INK, true));
        usageCard.addView(Ui.text(this, "So Focus Guard can show how much time you spend on each app. "
                + "On the next screen, find Focus Guard and turn it on.", 14, Ui.INK, false), Ui.top(this, 4));
        Button allow = Ui.button(this, "Allow usage access", false);
        allow.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        usageCard.addView(allow, Ui.top(this, 10));
        col.addView(usageCard, Ui.top(this, 12));

        dashContent = new LinearLayout(this);
        dashContent.setOrientation(LinearLayout.VERTICAL);
        col.addView(dashContent, Ui.top(this, 0));

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
        dashContent.addView(dayRow, Ui.top(this, 12));

        // Ring + legend + stats
        LinearLayout ringCard = Ui.card(this, Ui.CARD);
        ring = new RingView(this);
        ringCard.addView(ring);
        legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        ringCard.addView(legend, Ui.top(this, 12));
        LinearLayout stats = Ui.row(this);
        statUnlocks = stat(stats, 0);
        statApps = stat(stats, 10);
        ringCard.addView(stats, Ui.top(this, 14));
        dashContent.addView(ringCard, Ui.top(this, 10));

        // Week
        LinearLayout wk = Ui.card(this, Ui.CARD);
        wk.addView(Ui.text(this, "Last 7 days", 17, Ui.INK, true));
        weekBars = new BarView(this, 170);
        weekBars.setOnPick(this::pickDay);
        wk.addView(weekBars, Ui.top(this, 8));
        dashContent.addView(wk, Ui.top(this, 12));

        // Apps
        LinearLayout apps = Ui.card(this, Ui.CARD);
        apps.addView(Ui.text(this, "Apps", 17, Ui.INK, true));
        apps.addView(Ui.text(this, "Tap an app for its daily and hourly usage.", 13, Ui.MUTED, false),
                Ui.top(this, 2));
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        apps.addView(appList, Ui.top(this, 6));
        dashContent.addView(apps, Ui.top(this, 12));
    }

    private TextView stat(LinearLayout row, int startDp) {
        LinearLayout box = Ui.card(this, Ui.PAPER);
        box.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView t = Ui.text(this, "–", 15, Ui.INK, true);
        box.addView(t);
        row.addView(box, Ui.weight(this, 1f, startDp));
        return t;
    }

    private void loadDashboard() {
        boolean access = UsageData.hasAccess(this);
        usageCard.setVisibility(access ? View.GONE : View.VISIBLE);
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
                showDay();
            });
        }).start();
    }

    private void pickDay(int i) {
        if (week == null) return;
        dayIdx = Math.max(0, Math.min(6, i));
        showDay();
    }

    private void showDay() {
        if (week == null) return;
        UsageData.Day d = week[dayIdx];
        String name = dayIdx == 6 ? "Today" : dayIdx == 5 ? "Yesterday"
                : new SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(new Date(d.start));
        dayLabel.setText(name);
        Ui.enable(prevDay, dayIdx > 0);
        Ui.enable(nextDay, dayIdx < 6);

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(d.perApp.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        // Ring: top 5 apps + "Other"
        int top = Math.min(5, sorted.size());
        long other = 0;
        for (int i = top; i < sorted.size(); i++) other += sorted.get(i).getValue();
        long[] vals = new long[top + (other > 0 ? 1 : 0)];
        int[] cols = new int[vals.length];
        legend.removeAllViews();
        for (int i = 0; i < top; i++) {
            vals[i] = sorted.get(i).getValue();
            cols[i] = RING_COLORS[i];
            legend.addView(legendRow(cols[i], name(sorted.get(i).getKey()), vals[i]), Ui.top(this, 4));
        }
        if (other > 0) {
            vals[top] = other;
            cols[top] = RING_COLORS[5];
            legend.addView(legendRow(cols[top], "Other apps", other), Ui.top(this, 4));
        }
        ring.setData(vals, cols, Prefs.duration(d.total), name);
        statUnlocks.setText(d.unlocks < 0 ? "🔓 –" : "🔓 " + d.unlocks + " unlocks");
        statApps.setText("📱 " + sorted.size() + " apps used");

        // Week bars
        long[] wv = new long[7];
        String[] wl = new String[7];
        SimpleDateFormat f = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            wv[i] = week[i].total;
            wl[i] = i == 6 ? "Today" : f.format(new Date(week[i].start));
        }
        weekBars.setData(wv, wl, dayIdx);

        // App list
        appList.removeAllViews();
        if (sorted.isEmpty()) {
            appList.addView(Ui.text(this, "No app usage recorded for this day.", 14, Ui.MUTED, false),
                    Ui.top(this, 6));
        }
        long max = sorted.isEmpty() ? 1 : Math.max(1, sorted.get(0).getValue());
        Map<String, Integer> limits = Prefs.getLimits(this);
        for (Map.Entry<String, Long> e : sorted) {
            appList.addView(appRow(e.getKey(), e.getValue(), max, limits.get(e.getKey())));
        }
    }

    private String name(String pkg) {
        String n = labels.get(pkg);
        return n == null ? Apps.label(this, pkg) : n;
    }

    private View legendRow(int color, String label, long ms) {
        LinearLayout row = Ui.row(this);
        View dot = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        dot.setBackground(g);
        row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12)));
        row.addView(Ui.text(this, label, 15, Ui.INK, false), Ui.weight(this, 1f, 10));
        row.addView(Ui.text(this, Prefs.duration(ms), 15, Ui.MUTED, false));
        return row;
    }

    private View appRow(String pkg, long ms, long max, Integer limit) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        ImageView icon = new ImageView(this);
        Drawable d = icons.get(pkg);
        if (d != null) icon.setImageDrawable(d);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, name(pkg), 16, Ui.INK, false), Ui.weight(this, 1f, 0));
        top.addView(Ui.text(this, Prefs.duration(ms), 14, Ui.MUTED, false));
        mid.addView(top);

        LinearLayout track = new LinearLayout(this);
        track.setBackground(Ui.round(this, "#E4E9E6", 4));
        View fill = new View(this);
        fill.setBackground(Ui.round(this, "#5E9C8A", 4));
        float frac = Math.max(0.02f, ms / (float) max);
        track.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(this, 6), frac));
        View rest = new View(this);
        track.addView(rest, new LinearLayout.LayoutParams(0, Ui.dp(this, 6), 1f - frac + 0.0001f));
        mid.addView(track, Ui.top(this, 6));

        if (limit != null) {
            mid.addView(Ui.text(this, "⏳ Timer: " + limit + " min a day", 12, Ui.MUTED, false), Ui.top(this, 4));
        }
        row.addView(mid, Ui.weight(this, 1f, 12));
        row.setOnClickListener(v -> startActivity(new Intent(this, AppDetailActivity.class).putExtra("pkg", pkg)));
        return row;
    }

    // =====================================================================
    // Focus
    // =====================================================================

    private void buildFocus(LinearLayout col) {
        col.addView(Ui.text(this, "Focus", 28, Ui.INK, true));

        LinearLayout statusCard = Ui.card(this, Ui.INK);
        status = Ui.text(this, "", 20, Ui.PAPER, true);
        statusSub = Ui.text(this, "", 14, Ui.SOFT, false);
        statusCard.addView(status);
        statusCard.addView(statusSub, Ui.top(this, 4));
        col.addView(statusCard, Ui.top(this, 12));

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
            if (on && !Gray.canControl(this)) {
                Rules.toast(this, "Grayscale needs a one-time setup. See the Settings tab.");
            }
        });
        focusCard.addView(grayFocus, Ui.top(this, 12));
        col.addView(focusCard, Ui.top(this, 12));

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

        LinearLayout tips = Ui.card(this, Ui.CARD);
        tips.addView(Ui.text(this, "Start focus without opening the app", 17, Ui.INK, true));
        tips.addView(Ui.text(this,
                "• Notification bar: tap 30, 45 or 60 min on the Focus Guard notification.\n"
                        + "• Quick Settings: swipe down twice, tap ✏️ edit, and drag the \"Focus\" tile "
                        + "into your tiles.\n"
                        + "• Home screen: long-press the Focus Guard icon and pick a time.\n"
                        + "• Or set a Focus schedule in the Limits tab.",
                14, Ui.INK, false), Ui.top(this, 6));
        col.addView(tips, Ui.top(this, 12));
    }

    private void refreshStatus() {
        accCard.setVisibility(Rules.blockerOn(this) ? View.GONE : View.VISIBLE);
        weekCard.setVisibility(Rules.weekOver(this) ? View.VISIBLE : View.GONE);

        boolean focus = Rules.focusActive(this);
        long now = System.currentTimeMillis();
        if (Rules.emergencyOn(this)) {
            status.setText("Emergency time · " + Prefs.format(Prefs.getLong(this, "em_until") - now) + " left");
            statusSub.setText("Blocking comes back on automatically.");
        } else if (focus) {
            status.setText("Focus is ON · " + Prefs.format(Rules.focusLeft(this)) + " left");
            statusSub.setText(Rules.scheduleNow(this) && !Rules.focusOn(this)
                    ? "Your focus schedule is running."
                    : "Blocked apps, websites and settings are locked until it ends.");
        } else if (Rules.sleepNow(this)) {
            status.setText("Bedtime 🌙");
            statusSub.setText("Distracting apps are blocked until " + Prefs.clock(this, Prefs.sleepEnd(this)) + ".");
        } else {
            status.setText("Ready to focus");
            statusSub.setText("Pick a time and tap Start focus.");
        }

        startBtn.setText(focus ? "Focus is on" : "Start focus");
        Ui.enable(startBtn, !focus);
        Ui.enable(minutesInput, !focus);
        Ui.enable(grayFocus, !focus);
        for (Button b : chips) Ui.enable(b, !focus);
        highlightChips();

        int apps = Rules.blockedApps(this).size();
        int sites = Rules.blockedSites(this).size();
        listSummary.setText(apps + (apps == 1 ? " app" : " apps") + " · "
                + sites + (sites == 1 ? " website" : " websites"));
    }

    private void highlightChips() {
        String v = minutesInput.getText().toString().trim();
        for (int i = 0; i < chips.size(); i++) {
            Ui.setChip(this, chips.get(i), v.equals(String.valueOf(PRESETS[i])));
        }
    }

    private void confirmStart() {
        if (Rules.focusActive(this)) return;
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

    // =====================================================================
    // Limits
    // =====================================================================

    private void buildLimits(LinearLayout col) {
        col.addView(Ui.text(this, "Limits & bedtime", 28, Ui.INK, true));
        col.addView(Ui.text(this, "Your weekly plan: app timers, bedtime and focus schedule.", 14,
                Ui.MUTED, false), Ui.top(this, 2));

        weekCard = Ui.card(this, Ui.WARN);
        weekCard.addView(Ui.text(this, "Your plan's week is over", 16, Ui.INK, true));
        weekCard.addView(Ui.text(this, "Keep the same plan for another 7 days, or change it now. "
                + "Until you choose, your current plan stays on.", 14, Ui.INK, false), Ui.top(this, 6));
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

        planBody = new LinearLayout(this);
        planBody.setOrientation(LinearLayout.VERTICAL);
        col.addView(planBody, Ui.top(this, 0));
    }

    private void refreshPlan() {
        planBody.removeAllViews();
        boolean locked = Rules.planLocked(this);

        // Lock state + edit button
        LinearLayout lockCard = Ui.card(this, Ui.CARD);
        if (!Rules.hasPlan(this)) {
            lockCard.addView(Ui.text(this, "No plan yet", 16, Ui.INK, true));
            lockCard.addView(Ui.text(this, "Give apps like Facebook or TikTok a daily timer, set a bedtime, "
                    + "and schedule your study hours. Once saved, the plan is locked for 7 days.",
                    14, Ui.MUTED, false), Ui.top(this, 4));
        } else {
            lockCard.addView(Ui.text(this, locked
                    ? "🔒 Locked until " + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock"))
                    : "🔓 Unlocked: you can change it now", 15, Ui.INK, true));
        }
        Button open = Ui.button(this, !Rules.hasPlan(this) ? "Set up plan" : locked ? "View plan" : "Edit plan", false);
        open.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        lockCard.addView(open, Ui.top(this, 12));
        planBody.addView(lockCard, Ui.top(this, 12));

        // App timers
        Map<String, Integer> limits = Prefs.getLimits(this);
        LinearLayout timers = Ui.card(this, Ui.CARD);
        timers.addView(Ui.text(this, "⏳ App timers", 17, Ui.INK, true));
        if (limits.isEmpty()) {
            timers.addView(Ui.text(this, "None", 14, Ui.MUTED, false), Ui.top(this, 4));
        }
        for (Map.Entry<String, Integer> e : limits.entrySet()) {
            long used = Prefs.getUsage(this, e.getKey());
            long lim = e.getValue() * Rules.MIN;
            boolean done = used >= lim;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = Ui.row(this);
            top.addView(Ui.text(this, Apps.label(this, e.getKey()), 15, Ui.INK, false), Ui.weight(this, 1f, 0));
            top.addView(Ui.text(this, done ? "Time's up" : Prefs.duration(lim - used) + " left",
                    14, done ? Ui.RED : Ui.MUTED, done));
            r.addView(top);
            LinearLayout track = new LinearLayout(this);
            track.setBackground(Ui.round(this, "#E4E9E6", 4));
            float frac = Math.min(1f, Math.max(0.02f, used / (float) lim));
            View fill = new View(this);
            fill.setBackground(Ui.round(this, done ? Ui.RED : Ui.GOLD, 4));
            track.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(this, 6), frac));
            track.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 6), 1f - frac + 0.0001f));
            r.addView(track, Ui.top(this, 6));
            timers.addView(r, Ui.top(this, 10));
        }
        planBody.addView(timers, Ui.top(this, 12));

        // Bedtime
        LinearLayout bed = Ui.card(this, Ui.CARD);
        bed.addView(Ui.text(this, "🌙 Bedtime", 17, Ui.INK, true));
        String bedTxt = Prefs.sleepOn(this)
                ? Prefs.clock(this, Prefs.sleepStart(this)) + " – " + Prefs.clock(this, Prefs.sleepEnd(this))
                + " · " + Prefs.days(Prefs.sleepDays(this))
                + (Gray.sleepWanted(this) ? "\nGrayscale on" : "")
                + (Dnd.wanted(this) ? (Gray.sleepWanted(this) ? " · " : "\n") + "Do Not Disturb on" : "")
                : "Off";
        bed.addView(Ui.text(this, bedTxt, 14, Ui.MUTED, false), Ui.top(this, 4));
        planBody.addView(bed, Ui.top(this, 12));

        // Focus schedule
        LinearLayout sch = Ui.card(this, Ui.CARD);
        sch.addView(Ui.text(this, "🎯 Focus schedule", 17, Ui.INK, true));
        String schTxt = Prefs.schedOn(this)
                ? Prefs.clock(this, Prefs.schedStart(this)) + " – " + Prefs.clock(this, Prefs.schedEnd(this))
                + " · " + Prefs.days(Prefs.schedDays(this))
                : "Off";
        sch.addView(Ui.text(this, schTxt, 14, Ui.MUTED, false), Ui.top(this, 4));
        planBody.addView(sch, Ui.top(this, 12));
    }

    // =====================================================================
    // Settings
    // =====================================================================

    private void buildSettings(LinearLayout col) {
        col.addView(Ui.text(this, "Settings", 28, Ui.INK, true));
        settingsBody = new LinearLayout(this);
        settingsBody.setOrientation(LinearLayout.VERTICAL);
        col.addView(settingsBody, Ui.top(this, 0));
    }

    private void refreshSettings() {
        settingsBody.removeAllViews();

        LinearLayout perms = Ui.card(this, Ui.CARD);
        perms.addView(Ui.text(this, "Permissions", 17, Ui.INK, true));
        permRow(perms, "Blocker (Accessibility)", "Needed to block apps and websites.",
                Rules.blockerOn(this), "Turn on",
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        permRow(perms, "Usage access", "Needed for the screen-time dashboard.",
                UsageData.hasAccess(this), "Allow",
                () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        permRow(perms, "Notifications", "Start focus from your notification bar.",
                !needsNotifications(), "Allow", this::askNotifications);
        permRow(perms, "Do Not Disturb", "Turn on Do Not Disturb at bedtime.",
                Dnd.canControl(this), "Allow",
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        permRow(perms, "Grayscale", "Turn the screen gray at bedtime or during focus.",
                Gray.canControl(this), null, null);
        perms.addView(Ui.text(this, "If the Accessibility switch is greyed out: open App info, tap ⋮ (top right), "
                + "then \"Allow restricted settings\", and try again.", 12, Ui.MUTED, false), Ui.top(this, 12));
        Button info = Ui.button(this, "Open App info", false);
        info.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName()))));
        perms.addView(info, Ui.top(this, 8));
        settingsBody.addView(perms, Ui.top(this, 12));

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

        LinearLayout upd = Ui.card(this, Ui.CARD);
        upd.addView(Ui.text(this, "App updates", 17, Ui.INK, true));
        upd.addView(Ui.text(this, "Installed: build " + Updater.installedBuild(this), 14, Ui.MUTED, false),
                Ui.top(this, 2));
        Button check = Ui.button(this, "Check for updates", false);
        check.setOnClickListener(v -> Updater.check(this));
        upd.addView(check, Ui.top(this, 12));
        settingsBody.addView(upd, Ui.top(this, 12));
    }

    private void permRow(LinearLayout card, String title, String desc, boolean ok, String btn, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(Ui.text(this, (ok ? "✅ " : "⚪ ") + title, 15, Ui.INK, true));
        txt.addView(Ui.text(this, desc, 13, Ui.MUTED, false));
        row.addView(txt, Ui.weight(this, 1f, 0));
        if (!ok && btn != null) {
            Button b = Ui.chip(this, btn);
            Ui.setChip(this, b, true);
            b.setOnClickListener(v -> action.run());
            row.addView(b);
        }
        card.addView(row);
    }

    // =====================================================================
    // Lifecycle & permissions
    // =====================================================================

    @Override
    protected void onResume() {
        super.onResume();
        if (needsNotifications() && !Prefs.getBool(this, "asked_notif")) {
            Prefs.putBool(this, "asked_notif", true);
            requestPermissions(new String[]{NOTIF_PERM}, 7);
        }
        Notifier.update(this, true);
        refreshPlan();
        refreshSettings();
        refreshStatus();
        if (tab == 0) loadDashboard();
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
