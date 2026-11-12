package com.usama.focusguard;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared look for every screen. */
public final class Ui {

    private Ui() {}

    // Fixed colours (same in light and dark)
    public static final String DARK = "#1F3A33";
    public static final String LIGHT = "#F6F4EF";
    public static final String GOLD = "#F2C14E";
    public static final String SOFT = "#C9D6CF";

    // Theme colours (set by applyTheme)
    public static String INK = "#1F3A33";     // main text
    public static String PAPER = "#F6F4EF";   // page background
    public static String MUTED = "#5E6B66";   // secondary text
    public static String CARD = "#FFFFFF";    // card background
    public static String WARN = "#FBE7B5";    // warning card
    public static String RED = "#B3261E";
    public static String TRACK = "#E4E9E6";   // empty part of bars and rings
    public static boolean dark;

    /** 0 = follow phone, 1 = light, 2 = dark */
    public static int themeChoice(Context c) { return Prefs.sp(c).getInt("theme", 0); }

    public static boolean isDark(Context c) {
        int t = themeChoice(c);
        if (t == 1) return false;
        if (t == 2) return true;
        int night = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Call at the very start of onCreate, before super.onCreate. */
    public static void applyTheme(Activity a) {
        dark = isDark(a);
        if (dark) {
            INK = "#E8EFEB";
            PAPER = "#111816";
            MUTED = "#9FB0A9";
            CARD = "#1C2623";
            WARN = "#4A3E1E";
            RED = "#F2867B";
            TRACK = "#2E3A36";
            a.setTheme(android.R.style.Theme_Material_NoActionBar);
        } else {
            INK = "#1F3A33";
            PAPER = "#F6F4EF";
            MUTED = "#5E6B66";
            CARD = "#FFFFFF";
            WARN = "#FBE7B5";
            RED = "#B3261E";
            TRACK = "#E4E9E6";
            a.setTheme(android.R.style.Theme_Material_Light_NoActionBar);
        }
    }

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Creates a scrolling page, shows it, and returns the column to add views to. */
    public static LinearLayout page(Activity a) {
        a.getWindow().setStatusBarColor(Color.parseColor(PAPER));
        if (!dark) a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(Color.parseColor(PAPER));
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 18), dp(a, 22), dp(a, 18), dp(a, 40));
        scroll.addView(col);
        a.setContentView(scroll);
        return col;
    }

    public static TextView text(Context c, CharSequence s, int sp, String color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(0f, 1.15f);
        return t;
    }

    public static Button button(Context c, String text, boolean primary) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 18 : 15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.parseColor(primary ? DARK : LIGHT));
        b.setBackground(round(c, primary ? GOLD : (dark ? "#2E5248" : DARK), primary ? 26 : 12));
        b.setPadding(dp(c, 14), dp(c, primary ? 14 : 10), dp(c, 14), dp(c, primary ? 14 : 10));
        b.setStateListAnimator(null);
        return b;
    }

    public static Button chip(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 10), dp(c, 9), dp(c, 10), dp(c, 9));
        b.setStateListAnimator(null);
        setChip(c, b, false);
        return b;
    }

    public static void setChip(Context c, Button b, boolean on) {
        b.setBackground(round(c, on ? GOLD : PAPER, 20));
        b.setTextColor(Color.parseColor(on ? DARK : INK));
        b.setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
    }

    public static GradientDrawable round(Context c, String color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static LinearLayout card(Context c, String color) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackground(round(c, color, 16));
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams top(Context c, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    public static LinearLayout.LayoutParams weight(Context c, float w, int startDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.setMarginStart(dp(c, startDp));
        return lp;
    }

    public static void styleInput(Context c, EditText e) {
        e.setBackground(round(c, PAPER, 12));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setTextColor(Color.parseColor(INK));
    }

    public static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : 0.45f);
    }

    /** Small grey heading above a group, e.g. "WAYS TO DISCONNECT". */
    public static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.getDefault()), 12, MUTED, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 6), 0, 0, 0);
        return t;
    }

    /** Card with no inner padding, for lists of rows. */
    public static LinearLayout listCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, CARD, 18));
        l.setClipToOutline(true);
        return l;
    }

    /** A tappable row: round icon, title, subtitle and an arrow. */
    public static LinearLayout listRow(Context c, String emoji, String title, String sub,
                                       View.OnClickListener click) {
        LinearLayout row = row(c);
        row.setPadding(dp(c, 14), dp(c, 12), dp(c, 12), dp(c, 12));
        TextView ic = text(c, emoji, 19, INK, false);
        ic.setGravity(android.view.Gravity.CENTER);
        ic.setBackground(round(c, dark ? "#2A3733" : "#EDF2EF", 22));
        row.addView(ic, new LinearLayout.LayoutParams(dp(c, 42), dp(c, 42)));

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.addView(text(c, title, 16, INK, true));
        if (sub != null && !sub.isEmpty()) mid.addView(text(c, sub, 13, MUTED, false));
        row.addView(mid, weight(c, 1f, 14));

        if (click != null) {
            row.addView(text(c, "›", 24, MUTED, false));
            row.setOnClickListener(click);
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                row.setForeground(c.getDrawable(tv.resourceId));
            }
        }
        return row;
    }

    /** Thin line between rows of a list card. */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.parseColor(TRACK));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2 + 1));
        lp.setMarginStart(dp(c, 70));
        v.setLayoutParams(lp);
        return v;
    }
}
