package com.usama.focusguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String INK = "#1F3A33";
    private static final String PAPER = "#F6F4EF";
    private static final String GOLD = "#F2C14E";
    private static final String MUTED = "#5E6B66";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<CheckBox> appChecks = new ArrayList<>();
    private final List<View> lockables = new ArrayList<>();

    private TextView status;
    private LinearLayout accCard;
    private EditText minutesInput;
    private EditText sitesInput;
    private LinearLayout appsBox;
    private TextView appsLoading;
    private Button startBtn;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            if (Prefs.isActive(MainActivity.this)) handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor(INK));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor(PAPER));
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(24), dp(20), dp(40));
        scroll.addView(col);

        // Header
        TextView title = label("Focus Guard", 30, INK, true);
        col.addView(title);
        col.addView(label("Block distracting apps and websites while you study.", 15, MUTED, false),
                margins(dp(4)));

        // Status card
        status = label("", 18, PAPER, true);
        status.setPadding(dp(18), dp(18), dp(18), dp(18));
        status.setBackground(round(INK, 18));
        col.addView(status, margins(dp(20)));

        // Accessibility warning card
        accCard = new LinearLayout(this);
        accCard.setOrientation(LinearLayout.VERTICAL);
        accCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        accCard.setBackground(round("#FBE7B5", 16));
        accCard.addView(label("The blocker is switched off", 16, INK, true));
        accCard.addView(label("Turn on \"Focus Guard\" in Accessibility settings so it can block apps and websites. "
                + "If the switch is greyed out: open App info for Focus Guard, tap ⋮ (top right), "
                + "then \"Allow restricted settings\", and try again.", 14, INK, false), margins(dp(6)));
        Button accBtn = button("Open Accessibility settings", false);
        accBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        accCard.addView(accBtn, margins(dp(12)));
        Button infoBtn = button("Open App info", false);
        infoBtn.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        accCard.addView(infoBtn, margins(dp(8)));
        col.addView(accCard, margins(dp(16)));

        // Focus time
        col.addView(section("Focus time (minutes)"), margins(dp(24)));
        minutesInput = new EditText(this);
        minutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutesInput.setText(String.valueOf(Prefs.getMinutes(this)));
        minutesInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        minutesInput.setTextColor(Color.parseColor(INK));
        styleInput(minutesInput);
        col.addView(minutesInput, margins(dp(8)));
        lockables.add(minutesInput);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        int[] presets = {25, 30, 45, 60, 90};
        for (int p : presets) {
            Button b = button(String.valueOf(p), false);
            b.setOnClickListener(v -> minutesInput.setText(String.valueOf(p)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            quick.addView(b, lp);
            lockables.add(b);
        }
        col.addView(quick, margins(dp(10)));

        // Websites
        col.addView(section("Websites to block"), margins(dp(24)));
        col.addView(label("One per line, e.g. youtube.com. Works in Chrome, Edge, Brave, Firefox, "
                + "Samsung Internet and Opera.", 13, MUTED, false), margins(dp(4)));
        sitesInput = new EditText(this);
        sitesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        sitesInput.setMinLines(4);
        sitesInput.setGravity(Gravity.TOP | Gravity.START);
        sitesInput.setHint("youtube.com\nfacebook.com\ninstagram.com");
        sitesInput.setTextColor(Color.parseColor(INK));
        styleInput(sitesInput);
        List<String> savedSites = new ArrayList<>(Prefs.getSites(this));
        java.util.Collections.sort(savedSites);
        sitesInput.setText(android.text.TextUtils.join("\n", savedSites));
        col.addView(sitesInput, margins(dp(8)));
        lockables.add(sitesInput);

        // Apps
        col.addView(section("Apps to block"), margins(dp(24)));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        appsBox.setPadding(dp(8), dp(4), dp(8), dp(4));
        appsBox.setBackground(round("#FFFFFF", 14));
        appsLoading = label("Loading your apps…", 14, MUTED, false);
        appsLoading.setPadding(dp(8), dp(12), dp(8), dp(12));
        appsBox.addView(appsLoading);
        col.addView(appsBox, margins(dp(8)));

        // Start
        startBtn = button("Start focus", true);
        startBtn.setOnClickListener(v -> confirmStart());
        col.addView(startBtn, margins(dp(28)));

        setContentView(scroll);
        loadApps();
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
        if (!Prefs.isActive(this)) saveChoices();
    }

    // ---------- logic ----------

    private void refreshUi() {
        boolean active = Prefs.isActive(this);
        accCard.setVisibility(isBlockerOn() ? View.GONE : View.VISIBLE);

        if (active) {
            status.setText("Focus is ON  ·  " + Prefs.format(Prefs.remaining(this)) + " left\n"
                    + "Blocked apps, websites and these settings are locked until time is up.");
            startBtn.setText("Locked until focus ends");
        } else {
            status.setText("Ready to focus\nChoose what to block, set the time, and start.");
            startBtn.setText("Start focus");
        }
        startBtn.setEnabled(!active);
        startBtn.setAlpha(active ? 0.5f : 1f);
        for (View v : lockables) {
            v.setEnabled(!active);
            v.setAlpha(active ? 0.5f : 1f);
        }
        for (CheckBox c : appChecks) c.setEnabled(!active);
    }

    private void confirmStart() {
        if (Prefs.isActive(this)) return;

        if (!isBlockerOn()) {
            toast("Turn on the blocker in Accessibility settings first.");
            return;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(minutesInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            minutes = 0;
        }
        if (minutes < 1 || minutes > 720) {
            toast("Enter a focus time between 1 and 720 minutes.");
            return;
        }

        saveChoices();
        if (Prefs.getApps(this).isEmpty() && Prefs.getSites(this).isEmpty()) {
            toast("Pick at least one app or website to block.");
            return;
        }

        final int mins = minutes;
        new AlertDialog.Builder(this)
                .setTitle("Start a " + mins + "-minute focus?")
                .setMessage("Until it ends you won't be able to open the blocked apps or websites, "
                        + "change this list, or switch Focus Guard off. This can't be cancelled.")
                .setPositiveButton("Start", (d, w) -> {
                    Prefs.setEnd(this, System.currentTimeMillis() + mins * 60_000L);
                    toast("Focus started. Good luck!");
                    handler.removeCallbacks(tick);
                    handler.post(tick);
                })
                .setNegativeButton("Not yet", null)
                .show();
    }

    private void saveChoices() {
        // minutes
        try {
            int m = Integer.parseInt(minutesInput.getText().toString().trim());
            if (m > 0) Prefs.setMinutes(this, m);
        } catch (NumberFormatException ignored) {
        }
        // sites
        Set<String> sites = new HashSet<>();
        for (String line : sitesInput.getText().toString().split("[\\n,\\s]+")) {
            String d = Prefs.cleanDomain(line);
            if (!d.isEmpty()) sites.add(d);
        }
        Prefs.setSites(this, sites);
        // apps (only once the list has loaded, so we never wipe saved choices)
        if (!appChecks.isEmpty()) {
            Set<String> apps = new HashSet<>();
            for (CheckBox c : appChecks) {
                if (c.isChecked()) apps.add((String) c.getTag());
            }
            Prefs.setApps(this, apps);
        }
    }

    private boolean isBlockerOn() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, BlockerService.class);
        String all = enabled.toLowerCase(Locale.US);
        return all.contains(me.flattenToString().toLowerCase(Locale.US))
                || all.contains(me.flattenToShortString().toLowerCase(Locale.US));
    }

    private static class AppItem {
        String label;
        String pkg;
        Drawable icon;
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(launcher, 0);
            List<AppItem> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : infos) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                AppItem it = new AppItem();
                it.pkg = pkg;
                it.label = String.valueOf(ri.loadLabel(pm));
                try {
                    it.icon = ri.loadIcon(pm);
                } catch (Exception e) {
                    it.icon = null;
                }
                items.add(it);
            }
            items.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> showApps(items));
        }).start();
    }

    private void showApps(List<AppItem> items) {
        appsBox.removeView(appsLoading);
        Set<String> saved = Prefs.getApps(this);
        boolean active = Prefs.isActive(this);
        for (AppItem it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            ImageView icon = new ImageView(this);
            if (it.icon != null) icon.setImageDrawable(it.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

            CheckBox cb = new CheckBox(this);
            cb.setText(it.label);
            cb.setTag(it.pkg);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            cb.setTextColor(Color.parseColor(INK));
            cb.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // box on the right, name on the left
            cb.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            cb.setChecked(saved.contains(it.pkg));
            cb.setEnabled(!active);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(dp(12));
            row.addView(cb, lp);

            row.setOnClickListener(v -> {
                if (cb.isEnabled()) cb.toggle();
            });

            appsBox.addView(row);
            appChecks.add(cb);
        }
    }

    // ---------- view helpers ----------

    private TextView label(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(0f, 1.15f);
        return t;
    }

    private TextView section(String s) {
        return label(s, 18, INK, true);
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 18 : 15);
        b.setTextColor(Color.parseColor(primary ? INK : PAPER));
        b.setBackground(round(primary ? GOLD : INK, primary ? 28 : 12));
        b.setPadding(dp(12), dp(primary ? 16 : 10), dp(12), dp(primary ? 16 : 10));
        b.setStateListAnimator(null);
        return b;
    }

    private void styleInput(EditText e) {
        e.setBackground(round("#FFFFFF", 12));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
    }

    private GradientDrawable round(String color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
