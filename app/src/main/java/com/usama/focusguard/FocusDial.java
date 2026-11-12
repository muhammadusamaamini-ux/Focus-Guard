package com.usama.focusguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** The big round focus timer: a ring that empties as the time runs down. */
public class FocusDial extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float progress = 1f;
    private boolean running;
    private String center = "";
    private String sub = "";

    public FocusDial(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        small.setTextAlign(Paint.Align.CENTER);
    }

    /** progress: 1 = full ring, 0 = empty. */
    public void setState(float progress, boolean running, String center, String sub) {
        this.progress = progress;
        this.running = running;
        this.center = center;
        this.sub = sub;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int size = Math.min(width, Ui.dp(getContext(), 260));
        setMeasuredDimension(width, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float stroke = size * 0.055f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = size / 2f - stroke - 4f;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        track.setStrokeWidth(stroke);
        track.setColor(Color.parseColor(Ui.TRACK));
        canvas.drawCircle(cx, cy, r, track);

        arc.setStrokeWidth(stroke);
        arc.setColor(Color.parseColor(running ? Ui.GOLD : (Ui.dark ? "#7FC7B0" : "#5E9C8A")));
        float sweep = 360f * progress;
        if (sweep > 0.5f) canvas.drawArc(oval, -90f, sweep, false, arc);

        if (running && progress > 0f && progress < 1f) {
            double a = Math.toRadians(-90 + sweep);
            knob.setColor(Color.parseColor(Ui.GOLD));
            canvas.drawCircle(cx + (float) (r * Math.cos(a)), cy + (float) (r * Math.sin(a)), stroke * 0.9f, knob);
        }

        big.setColor(Color.parseColor(Ui.INK));
        big.setTextSize(size * (center.length() > 6 ? 0.15f : 0.19f));
        small.setColor(Color.parseColor(Ui.MUTED));
        small.setTextSize(size * 0.058f);
        canvas.drawText(center, cx, cy + big.getTextSize() * 0.3f, big);
        canvas.drawText(sub, cx, cy + big.getTextSize() * 0.3f + small.getTextSize() * 2f, small);
    }
}
