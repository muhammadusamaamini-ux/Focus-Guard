package com.usama.focusguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** A simple bar chart. Tapping a bar selects it. */
public class BarView extends View {

    public interface OnPick { void onPick(int index); }

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private long[] values = new long[0];
    private String[] labels = new String[0];
    private int selected = -1;
    private OnPick listener;
    private final int heightPx;

    public BarView(Context c, int heightDp) {
        super(c);
        heightPx = Ui.dp(c, heightDp);
        label.setColor(Color.parseColor(Ui.MUTED));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(Ui.dp(c, 11));
        value.setColor(Color.parseColor(Ui.INK));
        value.setTextAlign(Paint.Align.CENTER);
        value.setTextSize(Ui.dp(c, 11));
        value.setFakeBoldText(true);
    }

    public void setData(long[] values, String[] labels, int selected) {
        this.values = values;
        this.labels = labels;
        this.selected = selected;
        invalidate();
    }

    public void setOnPick(OnPick l) { listener = l; }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), heightPx);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        if (n == 0) return;
        float w = getWidth(), h = getHeight();
        float labelArea = Ui.dp(getContext(), 20);
        float topArea = Ui.dp(getContext(), 16);
        float chartH = h - labelArea - topArea;
        long max = 1;
        for (long v : values) max = Math.max(max, v);
        float slot = w / n;
        float bw = Math.max(Ui.dp(getContext(), 3), slot * 0.56f);
        float radius = Math.min(bw / 2f, Ui.dp(getContext(), 6));

        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2f;
            float bh = values[i] <= 0 ? 0 : Math.max(Ui.dp(getContext(), 3), chartH * values[i] / max);
            bar.setColor(Color.parseColor(i == selected ? Ui.GOLD : "#9DB5AC"));
            if (selected < 0) bar.setColor(Color.parseColor("#5E9C8A"));
            rect.set(cx - bw / 2f, topArea + chartH - bh, cx + bw / 2f, topArea + chartH);
            if (bh > 0) canvas.drawRoundRect(rect, radius, radius, bar);
            if (i < labels.length && labels[i] != null) {
                canvas.drawText(labels[i], cx, h - Ui.dp(getContext(), 5), label);
            }
            if (i == selected && values[i] > 0) {
                canvas.drawText(Prefs.duration(values[i]), cx, rect.top - Ui.dp(getContext(), 4), value);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null || values.length == 0) return false;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int idx = (int) (e.getX() / (getWidth() / (float) values.length));
            if (idx >= 0 && idx < values.length) listener.onPick(idx);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}
