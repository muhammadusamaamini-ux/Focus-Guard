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

    public static final String INK = "#1F3A33";
    public static final String PAPER = "#F6F4EF";
    public static final String GOLD = "#F2C14E";
    public static final String MUTED = "#5E6B66";
    public static final String CARD = "#FFFFFF";
    public static final String WARN = "#FBE7B5";
    public static final String RED = "#B3261E";
    public static final String SOFT = "#C9D6CF";

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Creates a scrolling page, shows it, and returns the column to add views to. */
    public static LinearLayout page(Activity a) {
        a.getWindow().setStatusBarColor(Color.parseColor(INK));
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
        b.setTextColor(Color.parseColor(primary ? INK : PAPER));
        b.setBackground(round(c, primary ? GOLD : INK, primary ? 26 : 12));
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
        b.setTextColor(Color.parseColor(INK));
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
}
