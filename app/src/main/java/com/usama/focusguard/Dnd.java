package com.usama.focusguard;

import android.app.NotificationManager;
import android.content.Context;

/** Turns on Do Not Disturb at bedtime (if chosen), and back off when bedtime ends. */
public final class Dnd {

    private Dnd() {}

    public static boolean canControl(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    public static boolean wanted(Context c) { return Prefs.getBool(c, "dnd_sleep"); }

    public static void apply(Context c) {
        if (!canControl(c)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        boolean want = Rules.sleepNow(c) && wanted(c);
        boolean ours = Prefs.getBool(c, "dnd_ours");
        try {
            int cur = nm.getCurrentInterruptionFilter();
            if (want && cur == NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                Prefs.putBool(c, "dnd_ours", true);
            } else if (!want && ours) {
                if (cur == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                }
                Prefs.putBool(c, "dnd_ours", false);
            }
        } catch (SecurityException ignored) {
        }
    }
}
