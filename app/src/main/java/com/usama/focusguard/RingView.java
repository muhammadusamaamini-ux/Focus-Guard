package com.usama.focusguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** A ring chart: each app is a coloured segment, total time in the middle. */
public class RingView extends View {

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private long[] values = new long[0];
    private int[] colors = new int[0];
    private String center = "";
    private String sub = "";

    public RingView(Context c) {
        super(c);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.BUTT);
        big.setColor(Color.parseColor(Ui.INK));
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        small.setColor(Color.parseColor(Ui.MUTED));
        small.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(long[] values, int[] colors, String center, String sub) {
        this.values = values;
        this.colors = colors;
        this.center = center;
        this.sub = sub;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int size = Math.min(width, Ui.dp(getContext(), 230));
        setMeasuredDimension(width, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float stroke = size * 0.12f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = size / 2f - stroke / 2f - 2f;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        arc.setStrokeWidth(stroke);

        long total = 0;
        for (long v : values) total += v;
        arc.setColor(Color.parseColor("#E4E9E6"));
        canvas.drawArc(oval, 0, 360, false, arc);
        if (total > 0) {
            float start = -90f;
            float gap = values.length > 1 ? 1.5f : 0f;
            for (int i = 0; i < values.length; i++) {
                float sweep = values[i] * 360f / total;
                if (sweep <= 0) continue;
                arc.setColor(colors[i % colors.length]);
                canvas.drawArc(oval, start + gap / 2f, Math.max(0.5f, sweep - gap), false, arc);
                start += sweep;
            }
        }

        big.setTextSize(size * 0.13f);
        small.setTextSize(size * 0.065f);
        canvas.drawText(center, cx, cy + big.getTextSize() * 0.2f, big);
        canvas.drawText(sub, cx, cy + big.getTextSize() * 0.2f + small.getTextSize() * 1.6f, small);
    }
}
