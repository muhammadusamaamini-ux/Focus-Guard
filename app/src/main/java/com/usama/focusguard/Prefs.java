package com.usama.focusguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Everything Focus Guard remembers, stored privately on the phone. */
public final class Prefs {

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("focus_guard", Context.MODE_PRIVATE);
    }

    private static SharedPreferences usage(Context c) {
        return c.getSharedPreferences("usage", Context.MODE_PRIVATE);
    }

    // ---------- generic ----------

    public static Set<String> getSet(Context c, String key) {
        return new HashSet<>(sp(c).getStringSet(key, new HashSet<>()));
    }

    public static void putSet(Context c, String key, Set<String> v) {
        sp(c).edit().putStringSet(key, new HashSet<>(v)).commit();
    }

    public static long getLong(Context c, String key) {
        return sp(c).getLong(key, 0L);
    }

    public static void putLong(Context c, String key, long v) {
        sp(c).edit().putLong(key, v).commit();
    }

    public static boolean getBool(Context c, String key) {
        return sp(c).getBoolean(key, false);
    }

    public static void putBool(Context c, String key, boolean v) {
        sp(c).edit().putBoolean(key, v).apply();
    }

    // ---------- distracting list ----------

    public static Set<String> getApps(Context c) { return getSet(c, "apps"); }

    public static void setApps(Context c, Set<String> v) { putSet(c, "apps", v); }

    public static Set<String> getSites(Context c) { return getSet(c, "sites"); }

    public static void setSites(Context c, Set<String> v) { putSet(c, "sites", v); }

    // ---------- focus ----------

    public static int getMinutes(Context c) { return sp(c).getInt("minutes", 30); }

    public static void setMinutes(Context c, int m) { sp(c).edit().putInt("minutes", m).apply(); }

    public static long getEnd(Context c) { return getLong(c, "end"); }

    public static void setEnd(Context c, long end) { putLong(c, "end", end); }

    public static long remaining(Context c) {
        return Math.max(0L, getEnd(c) - System.currentTimeMillis());
    }

    // ---------- weekly plan ----------

    /** package -> minutes per day */
    public static Map<String, Integer> getLimits(Context c) {
        Map<String, Integer> m = new TreeMap<>();
        for (String s : getSet(c, "limits")) {
            int i = s.lastIndexOf('=');
            if (i <= 0) continue;
            try {
                m.put(s.substring(0, i), Integer.parseInt(s.substring(i + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return m;
    }

    public static void setLimits(Context c, Map<String, Integer> m) {
        Set<String> s = new HashSet<>();
        for (Map.Entry<String, Integer> e : m.entrySet()) s.add(e.getKey() + "=" + e.getValue());
        putSet(c, "limits", s);
    }

    public static boolean sleepOn(Context c) { return sp(c).getBoolean("sleep_on", false); }

    public static int sleepStart(Context c) { return sp(c).getInt("sleep_start", 23 * 60); }

    public static int sleepEnd(Context c) { return sp(c).getInt("sleep_end", 6 * 60); }

    public static void setSleep(Context c, boolean on, int start, int end) {
        sp(c).edit().putBoolean("sleep_on", on).putInt("sleep_start", start)
                .putInt("sleep_end", end).commit();
    }

    // ---------- daily usage ----------

    public static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    public static long getUsage(Context c, String pkg) {
        SharedPreferences u = usage(c);
        if (!today().equals(u.getString("_day", ""))) return 0L;
        return u.getLong(pkg, 0L);
    }

    public static long addUsage(Context c, String pkg, long ms) {
        SharedPreferences u = usage(c);
        String t = today();
        SharedPreferences.Editor ed = u.edit();
        long cur;
        if (!t.equals(u.getString("_day", ""))) {
            ed.clear();
            ed.putString("_day", t);
            cur = 0L;
        } else {
            cur = u.getLong(pkg, 0L);
        }
        cur += ms;
        ed.putLong(pkg, cur).apply();
        return cur;
    }

    // ---------- formatting ----------

    /** 1:05:09 or 24:59 */
    public static String format(long ms) {
        long s = (Math.max(0L, ms) + 999L) / 1000L;
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    /** minute of day -> "11:00 PM" (or 23:00 if the phone uses 24-hour time) */
    public static String clock(Context c, int minuteOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(Calendar.MINUTE, minuteOfDay % 60);
        return android.text.format.DateFormat.getTimeFormat(c).format(cal.getTime());
    }

    /** "Sun 15 Nov, 3:27 PM" */
    public static String dateTime(Context c, long millis) {
        Date d = new Date(millis);
        return new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(d) + ", "
                + android.text.format.DateFormat.getTimeFormat(c).format(d);
    }

    /** "https://www.YouTube.com/watch?v=1" -> "youtube.com" */
    public static String cleanDomain(String raw) {
        if (raw == null) return "";
        String d = raw.trim().toLowerCase(Locale.US);
        int scheme = d.indexOf("://");
        if (scheme >= 0) d = d.substring(scheme + 3);
        if (d.startsWith("www.")) d = d.substring(4);
        for (int i = 0; i < d.length(); i++) {
            if ("/?# :".indexOf(d.charAt(i)) >= 0) {
                d = d.substring(0, i);
                break;
            }
        }
        return d;
    }
}
