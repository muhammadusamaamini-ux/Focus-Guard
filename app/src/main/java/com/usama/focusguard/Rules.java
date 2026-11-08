package com.usama.focusguard;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The rules: when blocking is on, emergency minutes, the weekly lock. */
public final class Rules {

    private Rules() {}

    public static final String FOCUS = "focus";
    public static final String SLEEP = "sleep";

    public static final long MIN = 60_000L;
    public static final long WEEK = 7L * 24L * 60L * MIN;

    // Emergency: 2 minutes per 20 minutes during focus, 5 minutes per hour at bedtime.
    public static long emLength(String mode) { return SLEEP.equals(mode) ? 5 * MIN : 2 * MIN; }

    public static long emPeriod(String mode) { return SLEEP.equals(mode) ? 60 * MIN : 20 * MIN; }

    // ---------- time windows ----------

    private static boolean dayOn(int days, int dow) { return (days & (1 << dow)) != 0; }

    /** Is "now" inside start..end (minutes of day) on one of the chosen days? Handles midnight. */
    static boolean inWindow(int start, int end, int days) {
        if (start == end) return false;
        Calendar cal = Calendar.getInstance();
        int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if (start < end) return m >= start && m < end && dayOn(days, dow);
        if (m >= start) return dayOn(days, dow);
        if (m < end) return dayOn(days, (dow + 6) % 7);   // after midnight: counts for yesterday
        return false;
    }

    /** Next time the clock reaches the given minute of day. */
    static long nextAt(int minuteOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(Calendar.MINUTE, minuteOfDay % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal.getTimeInMillis();
    }

    // ---------- focus & bedtime ----------

    /** A focus session started by hand (app, notification, tile or shortcut). */
    public static boolean focusOn(Context c) { return Prefs.remaining(c) > 0L; }

    /** Inside the scheduled focus hours from the weekly plan. */
    public static boolean scheduleNow(Context c) {
        return Prefs.schedOn(c) && inWindow(Prefs.schedStart(c), Prefs.schedEnd(c), Prefs.schedDays(c));
    }

    /** Focus blocking is on, from either a manual session or the schedule. */
    public static boolean focusActive(Context c) { return focusOn(c) || scheduleNow(c); }

    public static long focusEndsAt(Context c) {
        long end = focusOn(c) ? Prefs.getEnd(c) : 0L;
        if (scheduleNow(c)) end = Math.max(end, nextAt(Prefs.schedEnd(c)));
        return end;
    }

    public static long focusLeft(Context c) {
        return Math.max(0L, focusEndsAt(c) - System.currentTimeMillis());
    }

    public static boolean sleepNow(Context c) {
        return Prefs.sleepOn(c) && inWindow(Prefs.sleepStart(c), Prefs.sleepEnd(c), Prefs.sleepDays(c));
    }

    public static long sleepEndsAt(Context c) { return nextAt(Prefs.sleepEnd(c)); }

    /** FOCUS, SLEEP, or null when nothing is blocking right now. */
    public static String mode(Context c) {
        if (focusActive(c)) return FOCUS;
        if (sleepNow(c)) return SLEEP;
        return null;
    }

    // ---------- emergency ----------

    public static boolean emergencyOn(Context c) {
        return System.currentTimeMillis() < Prefs.getLong(c, "em_until");
    }

    public static long emAvailableAt(Context c, String mode) {
        return Prefs.getLong(c, "em_last_" + mode) + emPeriod(mode);
    }

    public static boolean emAvailable(Context c, String mode) {
        return System.currentTimeMillis() >= emAvailableAt(c, mode);
    }

    public static void useEmergency(Context c, String mode) {
        long now = System.currentTimeMillis();
        Prefs.putLong(c, "em_last_" + mode, now);
        Prefs.putLong(c, "em_until", now + emLength(mode));
        Notifier.update(c, true);
    }

    // ---------- weekly plan ----------

    public static boolean hasPlan(Context c) {
        return !Prefs.getLimits(c).isEmpty() || Prefs.sleepOn(c) || Prefs.schedOn(c);
    }

    public static boolean planLocked(Context c) {
        return hasPlan(c) && System.currentTimeMillis() < Prefs.getLong(c, "plan_lock");
    }

    public static boolean weekOver(Context c) {
        long lock = Prefs.getLong(c, "plan_lock");
        return hasPlan(c) && lock > 0L && System.currentTimeMillis() >= lock;
    }

    /** Locks the plan for 7 days, and freezes the current distracting list so it can't shrink. */
    public static void lockPlan(Context c) {
        Prefs.putLong(c, "plan_lock", System.currentTimeMillis() + WEEK);
        Prefs.putSet(c, "plan_apps", Prefs.getApps(c));
        Prefs.putSet(c, "plan_sites", Prefs.getSites(c));
    }

    public static Set<String> lockedApps(Context c) {
        return planLocked(c) ? Prefs.getSet(c, "plan_apps") : new HashSet<>();
    }

    public static Set<String> lockedSites(Context c) {
        return planLocked(c) ? Prefs.getSet(c, "plan_sites") : new HashSet<>();
    }

    public static Set<String> blockedApps(Context c) {
        Set<String> s = Prefs.getApps(c);
        s.addAll(lockedApps(c));
        return s;
    }

    public static Set<String> blockedSites(Context c) {
        Set<String> s = Prefs.getSites(c);
        s.addAll(lockedSites(c));
        return s;
    }

    /** While true, Focus Guard's own settings, Accessibility switch and uninstall are blocked. */
    public static boolean guardOn(Context c) {
        return focusActive(c) || sleepNow(c) || planLocked(c);
    }

    // ---------- starting focus ----------

    public static boolean blockerOn(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(c, BlockerService.class);
        String all = enabled.toLowerCase(Locale.US);
        return all.contains(me.flattenToString().toLowerCase(Locale.US))
                || all.contains(me.flattenToShortString().toLowerCase(Locale.US));
    }

    /** Starts focus from anywhere (app, notification, tile, home-screen shortcut). */
    public static boolean startFocus(Context c, int minutes) {
        if (focusActive(c)) {
            toast(c, "Focus is already on · " + Prefs.format(focusLeft(c)) + " left");
            return false;
        }
        if (!blockerOn(c)) {
            toast(c, "Open Focus Guard and turn on the blocker first.");
            return false;
        }
        if (blockedApps(c).isEmpty() && blockedSites(c).isEmpty()) {
            toast(c, "Open Focus Guard and choose your distracting apps first.");
            return false;
        }
        if (minutes < 1 || minutes > 720) {
            toast(c, "Choose between 1 and 720 minutes.");
            return false;
        }
        Prefs.setEnd(c, System.currentTimeMillis() + minutes * MIN);
        Notifier.update(c, true);
        refreshTile(c);
        toast(c, "Focus started · " + minutes + " min. Good luck!");
        return true;
    }

    public static void refreshTile(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, FocusTileService.class));
        } catch (Exception ignored) {
        }
    }

    static void toast(Context c, String s) {
        Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }
}
