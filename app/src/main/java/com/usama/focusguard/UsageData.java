package com.usama.focusguard;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads screen time from Android's usage history (needs "Usage access"). */
public final class UsageData {

    private UsageData() {}

    private static final int RESUMED = 1;          // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private static final int PAUSED = 2;           // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private static final int SCREEN_OFF = 16;      // SCREEN_NON_INTERACTIVE
    private static final int KEYGUARD_SHOWN = 17;
    private static final int KEYGUARD_HIDDEN = 18; // phone unlocked

    public static final long DAY = 24L * 60L * 60L * 1000L;

    public static class Session {
        public final String pkg;
        public final long from;
        public final long to;

        Session(String pkg, long from, long to) {
            this.pkg = pkg;
            this.from = from;
            this.to = to;
        }
    }

    /** Everything about one day, ready for the screens. */
    public static class Day {
        public long start;
        public long total;
        public int unlocks = -1;
        public final Map<String, Long> perApp = new HashMap<>();
        public final Map<String, Integer> opens = new HashMap<>();
        public final List<Session> sessions = new ArrayList<>();
    }

    public static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Midnight at the start of the day, daysAgo days back (0 = today). */
    public static long dayStart(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, -daysAgo);
        return cal.getTimeInMillis();
    }

    /** Home screen, keyboard, system bars and Focus Guard itself don't count as screen time. */
    public static Set<String> excluded(Context c) {
        Set<String> s = new HashSet<>();
        s.add(c.getPackageName());
        s.add("com.android.systemui");
        s.add("android");
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(home, 0)) {
                s.add(ri.activityInfo.packageName);
            }
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) for (InputMethodInfo i : imm.getEnabledInputMethodList()) s.add(i.getPackageName());
        } catch (Exception ignored) {
        }
        return s;
    }

    /** Slow: call from a background thread. */
    public static Day day(Context c, int daysAgo, Set<String> excluded) {
        Day d = new Day();
        d.start = dayStart(daysAgo);
        long end = Math.min(d.start + DAY, System.currentTimeMillis());
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null || end <= d.start) return d;

        UsageEvents events = usm.queryEvents(d.start - 3L * 60L * 60L * 1000L, end);
        UsageEvents.Event e = new UsageEvents.Event();
        String cur = null;
        long curStart = 0L;
        int unlocks = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            long ts = e.getTimeStamp();
            String p = e.getPackageName();
            if (type == RESUMED) {
                if (cur != null && !cur.equals(p)) add(d, cur, curStart, ts, end);
                if (cur == null || !cur.equals(p)) {
                    cur = p;
                    curStart = ts;
                }
            } else if (type == PAUSED) {
                if (p != null && p.equals(cur)) {
                    add(d, cur, curStart, ts, end);
                    cur = null;
                }
            } else if (type == SCREEN_OFF || type == KEYGUARD_SHOWN) {
                if (cur != null) {
                    add(d, cur, curStart, ts, end);
                    cur = null;
                }
            } else if (type == KEYGUARD_HIDDEN && ts >= d.start) {
                unlocks++;
            }
        }
        if (cur != null) add(d, cur, curStart, end, end);
        if (Build.VERSION.SDK_INT >= 28) d.unlocks = unlocks;

        String prevPkg = null;
        long prevTo = 0L;
        for (Session s : d.sessions) {
            if (excluded.contains(s.pkg)) {
                prevPkg = s.pkg;
                prevTo = s.to;
                continue;
            }
            long len = s.to - s.from;
            d.total += len;
            Long had = d.perApp.get(s.pkg);
            d.perApp.put(s.pkg, (had == null ? 0L : had) + len);
            if (!s.pkg.equals(prevPkg) || s.from - prevTo > 30_000L) {
                Integer o = d.opens.get(s.pkg);
                d.opens.put(s.pkg, (o == null ? 0 : o) + 1);
            }
            prevPkg = s.pkg;
            prevTo = s.to;
        }
        return d;
    }

    private static void add(Day d, String pkg, long from, long to, long end) {
        long a = Math.max(from, d.start);
        long b = Math.min(to, end);
        if (pkg != null && b > a) d.sessions.add(new Session(pkg, a, b));
    }

    /** Screen time per hour (24 values) for one app, or for everything when pkg is null. */
    public static long[] hourly(Day d, String pkg, Set<String> excluded) {
        long[] h = new long[24];
        long hour = 60L * 60L * 1000L;
        for (Session s : d.sessions) {
            if (pkg != null ? !pkg.equals(s.pkg) : excluded.contains(s.pkg)) continue;
            long a = s.from;
            while (a < s.to) {
                int idx = (int) ((a - d.start) / hour);
                if (idx < 0 || idx > 23) break;
                long bucketEnd = d.start + (idx + 1) * hour;
                long b = Math.min(s.to, bucketEnd);
                h[idx] += b - a;
                a = b;
            }
        }
        return h;
    }
}
