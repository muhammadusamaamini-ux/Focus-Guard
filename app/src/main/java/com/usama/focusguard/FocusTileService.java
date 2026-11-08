package com.usama.focusguard;

import android.app.AlertDialog;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** The "Focus" tile in Quick Settings (swipe down from the top of the screen). */
public class FocusTileService extends TileService {

    private static final int[] TIMES = {25, 30, 45, 60, 90};

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (Rules.focusActive(this)) {
            Rules.toast(this, "Focus is on · " + Prefs.format(Rules.focusLeft(this)) + " left");
            refresh();
            return;
        }
        String[] items = new String[TIMES.length];
        for (int i = 0; i < TIMES.length; i++) items[i] = TIMES[i] + " minutes";
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Start focus")
                .setItems(items, (dlg, w) -> {
                    Rules.startFocus(this, TIMES[w]);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .create();
        showDialog(d);
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = Rules.focusActive(this);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(on ? "Focus on" : "Focus");
        if (Build.VERSION.SDK_INT >= 29) {
            long mins = (Rules.focusLeft(this) + Rules.MIN - 1) / Rules.MIN;
            t.setSubtitle(on ? mins + " min left" : "Tap to start");
        }
        t.updateTile();
    }
}
