package com.usama.focusguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The full-screen "stay focused" wall shown when a blocked app or site is opened. */
public class BlockActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView title;
    private TextView message;
    private TextView timer;
    private Button button;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            if (Prefs.isActive(BlockActivity.this)) handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(Color.parseColor("#1F3A33"));
        int pad = dp(32);
        col.setPadding(pad, pad, pad, pad);

        TextView icon = text("🔒", 56, "#F2C14E", false);
        title = text("Stay focused", 30, "#F6F4EF", true);
        message = text("This is blocked during your focus time.", 17, "#C9D6CF", false);
        timer = text("", 48, "#F2C14E", true);
        timer.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        button = new Button(this);
        button.setText("Back to studying");
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        button.setTextColor(Color.parseColor("#1F3A33"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F2C14E"));
        bg.setCornerRadius(dp(28));
        button.setBackground(bg);
        button.setPadding(dp(28), dp(14), dp(28), dp(14));
        button.setOnClickListener(v -> goHome());

        col.addView(icon);
        col.addView(title, spaced(dp(12)));
        col.addView(message, spaced(dp(8)));
        col.addView(timer, spaced(dp(28)));
        col.addView(button, spaced(dp(36)));

        setContentView(col);
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
    }

    private void update() {
        if (Prefs.isActive(this)) {
            timer.setText(Prefs.format(Prefs.remaining(this)));
            message.setText("This is blocked during your focus time.\nTime left:");
        } else {
            title.setText("Focus finished");
            message.setText("Well done. Everything is unlocked now.");
            timer.setText("");
            button.setText("Done");
        }
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams spaced(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = top;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
