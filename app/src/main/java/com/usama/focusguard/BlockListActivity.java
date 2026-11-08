package com.usama.focusguard;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Choose the distracting apps and websites. Saved automatically when you leave. */
public class BlockListActivity extends Activity {

    private final List<CheckBox> checks = new ArrayList<>();
    private LinearLayout lockCard;
    private TextView lockText;
    private EditText sitesInput;
    private LinearLayout appsBox;
    private TextView loading;
    private boolean fullyLocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout col = Ui.page(this);

        col.addView(Ui.text(this, "Distracting apps & websites", 26, Ui.INK, true));
        col.addView(Ui.text(this, "These are blocked during focus time and bedtime. Changes save "
                + "automatically.", 14, Ui.MUTED, false), Ui.top(this, 4));

        lockCard = Ui.card(this, Ui.WARN);
        lockText = Ui.text(this, "", 14, Ui.INK, false);
        lockCard.addView(lockText);
        col.addView(lockCard, Ui.top(this, 14));

        // Websites
        LinearLayout sitesCard = Ui.card(this, Ui.CARD);
        sitesCard.addView(Ui.text(this, "Websites", 17, Ui.INK, true));
        sitesCard.addView(Ui.text(this, "One per line, e.g. youtube.com. Works in Chrome, Edge, Brave, "
                + "Firefox, Samsung Internet and Opera.", 13, Ui.MUTED, false), Ui.top(this, 2));
        sitesInput = new EditText(this);
        sitesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        sitesInput.setMinLines(4);
        sitesInput.setGravity(Gravity.TOP | Gravity.START);
        sitesInput.setHint("youtube.com\nfacebook.com\ninstagram.com");
        Ui.styleInput(this, sitesInput);
        List<String> saved = new ArrayList<>(Prefs.getSites(this));
        Collections.sort(saved);
        sitesInput.setText(TextUtils.join("\n", saved));
        sitesCard.addView(sitesInput, Ui.top(this, 8));
        col.addView(sitesCard, Ui.top(this, 12));

        // Apps
        LinearLayout appsCard = Ui.card(this, Ui.CARD);
        appsCard.addView(Ui.text(this, "Apps", 17, Ui.INK, true));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        loading = Ui.text(this, "Loading your apps…", 14, Ui.MUTED, false);
        appsBox.addView(loading);
        appsCard.addView(appsBox, Ui.top(this, 8));
        col.addView(appsCard, Ui.top(this, 12));

        new Thread(() -> {
            List<Apps.Item> items = Apps.load(this);
            runOnUiThread(() -> showApps(items));
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyLock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void applyLock() {
        fullyLocked = Rules.focusOn(this) || Rules.sleepNow(this);
        Set<String> frozen = Rules.lockedApps(this);
        if (fullyLocked) {
            lockText.setText("🔒 Locked while focus time or bedtime is on.");
            lockCard.setVisibility(View.VISIBLE);
        } else if (Rules.planLocked(this)) {
            lockText.setText("🔒 Your weekly plan is locked until "
                    + Prefs.dateTime(this, Prefs.getLong(this, "plan_lock"))
                    + ". You can add more apps and websites, but the ones you had when you locked it "
                    + "stay on the list until then.");
            lockCard.setVisibility(View.VISIBLE);
        } else {
            lockCard.setVisibility(View.GONE);
        }
        Ui.enable(sitesInput, !fullyLocked);
        for (CheckBox c : checks) {
            boolean frozenItem = frozen.contains((String) c.getTag());
            if (frozenItem) c.setChecked(true);
            c.setEnabled(!fullyLocked && !frozenItem);
        }
    }

    private void save() {
        if (Rules.focusOn(this) || Rules.sleepNow(this)) return;

        Set<String> sites = new HashSet<>();
        for (String line : sitesInput.getText().toString().split("[\\n,\\s]+")) {
            String d = Prefs.cleanDomain(line);
            if (!d.isEmpty()) sites.add(d);
        }
        sites.addAll(Rules.lockedSites(this));
        Prefs.setSites(this, sites);

        if (!checks.isEmpty()) {
            Set<String> apps = new HashSet<>();
            for (CheckBox c : checks) if (c.isChecked()) apps.add((String) c.getTag());
            apps.addAll(Rules.lockedApps(this));
            Prefs.setApps(this, apps);
        }
    }

    private void showApps(List<Apps.Item> items) {
        appsBox.removeView(loading);
        Set<String> saved = Rules.blockedApps(this);
        for (Apps.Item it : items) {
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));

            ImageView icon = new ImageView(this);
            if (it.icon != null) icon.setImageDrawable(it.icon);
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));

            CheckBox cb = new CheckBox(this);
            cb.setText(it.label);
            cb.setTag(it.pkg);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            cb.setTextColor(Color.parseColor(Ui.INK));
            cb.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            cb.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            cb.setChecked(saved.contains(it.pkg));
            row.addView(cb, Ui.weight(this, 1f, 12));
            row.setOnClickListener(v -> {
                if (cb.isEnabled()) cb.toggle();
            });

            appsBox.addView(row);
            checks.add(cb);
        }
        applyLock();
    }
}
