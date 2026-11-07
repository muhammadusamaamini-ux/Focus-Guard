package com.usama.focusguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Small helper that stores the block lists and the focus end time on the phone. */
public final class Prefs {

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("focus_guard", Context.MODE_PRIVATE);
    }

    public static Set<String> getApps(Context c) {
        return new HashSet<>(sp(c).getStringSet("apps", new HashSet<>()));
    }

    public static void setApps(Context c, Set<String> apps) {
        sp(c).edit().putStringSet("apps", new HashSet<>(apps)).apply();
    }

    public static Set<String> getSites(Context c) {
        return new HashSet<>(sp(c).getStringSet("sites", new HashSet<>()));
    }

    public static void setSites(Context c, Set<String> sites) {
        sp(c).edit().putStringSet("sites", new HashSet<>(sites)).apply();
    }

    public static int getMinutes(Context c) {
        return sp(c).getInt("minutes", 30);
    }

    public static void setMinutes(Context c, int minutes) {
        sp(c).edit().putInt("minutes", minutes).apply();
    }

    public static long getEnd(Context c) {
        return sp(c).getLong("end", 0L);
    }

    public static void setEnd(Context c, long endMillis) {
        sp(c).edit().putLong("end", endMillis).commit();
    }

    public static long remaining(Context c) {
        return Math.max(0L, getEnd(c) - System.currentTimeMillis());
    }

    public static boolean isActive(Context c) {
        return remaining(c) > 0L;
    }

    /** 1:05:09 or 24:59 */
    public static String format(long ms) {
        long s = (ms + 999L) / 1000L;
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    /** "https://www.YouTube.com/watch?v=1" -> "youtube.com" */
    public static String cleanDomain(String raw) {
        if (raw == null) return "";
        String d = raw.trim().toLowerCase(Locale.US);
        int scheme = d.indexOf("://");
        if (scheme >= 0) d = d.substring(scheme + 3);
        if (d.startsWith("www.")) d = d.substring(4);
        int cut = indexOfAny(d, "/?# :");
        if (cut >= 0) d = d.substring(0, cut);
        return d;
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }
}
