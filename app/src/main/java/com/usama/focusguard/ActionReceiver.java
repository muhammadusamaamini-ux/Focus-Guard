package com.usama.focusguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the "30 min / 45 min / 60 min" buttons on the notification. */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int minutes = i.getIntExtra("minutes", 0);
        if (minutes > 0) Rules.startFocus(c, minutes);
    }
}
