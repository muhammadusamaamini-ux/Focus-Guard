package com.usama.focusguard;

import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;

/** A row of seven day buttons (Mon … Sun) for choosing days of the week. */
public class DayPicker extends LinearLayout {

    private static final String[] LETTERS = {"S", "M", "T", "W", "T", "F", "S"};
    private final Button[] buttons = new Button[7];
    private int days;

    public DayPicker(Context c, int initialDays) {
        super(c);
        setOrientation(HORIZONTAL);
        days = initialDays;
        for (int k = 0; k < 7; k++) {
            final int d = (k + 1) % 7;   // Monday first, Sunday last
            Button b = Ui.chip(c, LETTERS[d]);
            b.setOnClickListener(v -> {
                days ^= (1 << d);
                refresh();
            });
            buttons[d] = b;
            addView(b, Ui.weight(c, 1f, k == 0 ? 0 : 4));
        }
        refresh();
    }

    public int getDays() { return days; }

    @Override
    public void setEnabled(boolean on) {
        super.setEnabled(on);
        for (Button b : buttons) Ui.enable(b, on);
    }

    private void refresh() {
        for (int d = 0; d < 7; d++) Ui.setChip(getContext(), buttons[d], (days & (1 << d)) != 0);
    }
}
