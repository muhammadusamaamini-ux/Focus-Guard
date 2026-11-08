package com.usama.focusguard;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Turns the whole screen grayscale (Android's built-in colour-correction "Grayscale" mode)
 * at bedtime and, if chosen, during focus. Android only allows this after a one-time
 * permission grant from a computer:
 * adb shell pm grant com.usama.focusguard android.permission.WRITE_SECURE_SETTINGS
 */
public final class Gray {

    private Gray() {}

    public static final String GRANT_COMMAND =
            "adb shell pm grant com.usama.focusguard android.permission.WRITE_SECURE_SETTINGS";

    private static final String ENABLED = "accessibility_display_daltonizer_enabled";
    private static final String MODE = "accessibility_display_daltonizer";
    private static final int MODE_GRAY = 0;

    public static boolean canControl(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean focusWanted(Context c) { return Prefs.getBool(c, "gray_focus"); }

    public static boolean sleepWanted(Context c) { return Prefs.sp(c).getBoolean("gray_sleep", true); }

    /** Is grayscale wanted for any setting, so the one-time setup matters? */
    public static boolean anyWanted(Context c) {
        return focusWanted(c) || (Prefs.sleepOn(c) && sleepWanted(c));
    }

    private static boolean isGray(ContentResolver r) {
        return Settings.Secure.getInt(r, ENABLED, 0) == 1 && Settings.Secure.getInt(r, MODE, -1) == MODE_GRAY;
    }

    /** Called every few seconds by the blocker: grayscale on or off as needed. */
    public static void apply(Context c) {
        if (!canControl(c)) return;
        boolean want = (Rules.focusOn(c) && focusWanted(c)) || (Rules.sleepNow(c) && sleepWanted(c));
        boolean ours = Prefs.getBool(c, "gray_ours");
        ContentResolver r = c.getContentResolver();
        try {
            if (want && !isGray(r)) {
                if (!ours) {
                    Prefs.putLong(c, "gray_prev_en", Settings.Secure.getInt(r, ENABLED, 0));
                    Prefs.putLong(c, "gray_prev_mode", Settings.Secure.getInt(r, MODE, 12));
                }
                Settings.Secure.putInt(r, ENABLED, 1);
                Settings.Secure.putInt(r, MODE, MODE_GRAY);
                Prefs.sp(c).edit().putBoolean("gray_ours", true).commit();
            } else if (!want && ours) {
                Settings.Secure.putInt(r, ENABLED, (int) Prefs.getLong(c, "gray_prev_en"));
                Settings.Secure.putInt(r, MODE, (int) Prefs.getLong(c, "gray_prev_mode"));
                Prefs.sp(c).edit().putBoolean("gray_ours", false).commit();
            }
        } catch (SecurityException ignored) {
            // Permission was taken away.
        }
    }
}
