package com.usama.focusguard;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Banking mode. Banking apps refuse to run while a downloaded app has Accessibility on.
 * When you open one of your banking apps, Focus Guard switches its blocker off, and switches
 * it back on by itself as soon as you leave the banking app (or the screen turns off).
 * This needs the one-time grayscale permission (WRITE_SECURE_SETTINGS) from a computer.
 */
public final class Bank {

    private Bank() {}

    private static final String KEY_APPS = "bank_apps";
    private static final long CHECK_EVERY = 15_000L;
    private static final long MAX_PAUSE = 30 * Rules.MIN;

    // ---------- list of banking apps ----------

    public static Set<String> apps(Context c) { return Prefs.getSet(c, KEY_APPS); }

    public static void add(Context c, String pkg) {
        Set<String> s = apps(c);
        s.add(pkg);
        Prefs.putSet(c, KEY_APPS, s);
        updateShortcuts(c);
    }

    public static void remove(Context c, String pkg) {
        Set<String> s = apps(c);
        s.remove(pkg);
        Prefs.putSet(c, KEY_APPS, s);
        updateShortcuts(c);
    }

    /** Why an app can't be a banking app, or null if it can. Stops banking mode becoming a loophole. */
    public static String notAllowed(Context c, String pkg) {
        if (Rules.blockedApps(c).contains(pkg)) return "It's on your distracting list.";
        if (Prefs.getLimits(c).containsKey(pkg)) return "It has an app timer.";
        if (browsers(c).contains(pkg)) return "Browsers can't be banking apps.";
        if (pkg.equals(c.getPackageName()) || pkg.contains("settings") || pkg.contains("packageinstaller")) {
            return "This app can't be added.";
        }
        return null;
    }

    public static Set<String> browsers(Context c) {
        Set<String> s = new HashSet<>();
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"));
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(web, 0)) {
                s.add(ri.activityInfo.packageName);
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    // ---------- switching the blocker ----------

    /** Can Focus Guard switch its own Accessibility on and off? (one-time computer setup) */
    public static boolean canSwitch(Context c) { return Gray.canControl(c); }

    public static boolean active(Context c) { return Prefs.getBool(c, "bank_mode"); }

    public static String activeApp(Context c) { return Prefs.sp(c).getString("bank_pkg", null); }

    private static String me(Context c) {
        return new ComponentName(c, BlockerService.class).flattenToString();
    }

    private static List<String> enabledList(ContentResolver r) {
        List<String> out = new ArrayList<>();
        String s = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s != null) for (String p : s.split(":")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    private static boolean isMine(Context c, String entry) {
        ComponentName cn = ComponentName.unflattenFromString(entry);
        return cn != null && cn.getPackageName().equals(c.getPackageName());
    }

    static void blockerOff(Context c) {
        ContentResolver r = c.getContentResolver();
        List<String> keep = new ArrayList<>();
        for (String e : enabledList(r)) if (!isMine(c, e)) keep.add(e);
        Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", keep));
        if (keep.isEmpty()) Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
    }

    static void blockerOn(Context c) {
        ContentResolver r = c.getContentResolver();
        List<String> list = enabledList(r);
        boolean has = false;
        for (String e : list) if (isMine(c, e)) has = true;
        if (!has) list.add(me(c));
        Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", list));
        Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }

    /** Turns the blocker off for a banking app and starts watching for when to turn it back on. */
    private static boolean pause(Context c, String pkg) {
        if (!canSwitch(c)) return false;
        Prefs.sp(c).edit().putBoolean("bank_mode", true).putString("bank_pkg", pkg)
                .putLong("bank_since", System.currentTimeMillis()).commit();
        Notifier.update(c, true);
        scheduleCheck(c);
        try {
            blockerOff(c);
        } catch (SecurityException e) {
            Prefs.sp(c).edit().putBoolean("bank_mode", false).commit();
            return false;
        }
        return true;
    }

    /** Switches the blocker back on. */
    public static void resume(Context c) {
        boolean was = active(c);
        Prefs.sp(c).edit().putBoolean("bank_mode", false).commit();
        cancelCheck(c);
        if (was && canSwitch(c)) {
            try {
                blockerOn(c);
            } catch (SecurityException ignored) {
            }
        }
        Notifier.update(c, true);
    }

    /** Called by the blocker when a banking app is opened directly. */
    public static void openedDirectly(Context c, String pkg) {
        if (active(c) || !canSwitch(c)) return;
        if (pause(c, pkg)) {
            Rules.toast(c, "🏦 Banking mode on. If " + Apps.label(c, pkg)
                    + " shows a warning, close it and open it again.");
        }
    }

    /** "Open" button / shortcut: switch the blocker off first, then open the banking app. */
    public static void open(Activity a, String pkg, Runnable afterLaunch) {
        if (!canSwitch(a)) {
            new AlertDialog.Builder(a)
                    .setTitle("One-time setup needed")
                    .setMessage("To switch the blocker off for banking apps, Focus Guard needs the same one-time "
                            + "permission as grayscale. Run this command once from your laptop:\n\n"
                            + Gray.GRANT_COMMAND)
                    .setPositiveButton("OK", (d, w) -> { if (afterLaunch != null) afterLaunch.run(); })
                    .setOnCancelListener(d -> { if (afterLaunch != null) afterLaunch.run(); })
                    .show();
            return;
        }
        pause(a, pkg);
        // Give Android a moment to switch Accessibility off before the bank app checks for it.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    a.startActivity(i);
                } catch (Exception ignored) {
                }
            }
            if (afterLaunch != null) afterLaunch.run();
        }, 900L);
    }

    // ---------- watching for when to switch back on ----------

    /** Runs every 15 seconds while banking mode is on. */
    public static void check(Context c) {
        if (!active(c)) return;
        String bank = activeApp(c);
        long since = Prefs.getLong(c, "bank_since");
        long now = System.currentTimeMillis();
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm == null || pm.isInteractive();

        boolean back;
        if (!screenOn || now - since > MAX_PAUSE) {
            back = true;
        } else if (UsageData.hasAccess(c)) {
            String fg = foreground(c);
            back = now - since > 20_000L && fg != null && !fg.equals(bank);
        } else {
            back = now - since > 5 * Rules.MIN;
        }
        if (back) resume(c);
        else scheduleCheck(c);
    }

    /** The app most recently brought to the front. */
    private static String foreground(Context c) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        long now = System.currentTimeMillis();
        UsageEvents ev = usm.queryEvents(now - 30 * Rules.MIN, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String last = null;
        Set<String> skip = new HashSet<>();
        skip.add("com.android.systemui");
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == 1 && !skip.contains(e.getPackageName())) last = e.getPackageName();
        }
        return last;
    }

    private static PendingIntent checkIntent(Context c) {
        Intent i = new Intent(c, BankReceiver.class).setAction("com.usama.focusguard.BANK_CHECK");
        return PendingIntent.getBroadcast(c, 77, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void scheduleCheck(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + CHECK_EVERY;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, checkIntent(c));
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, checkIntent(c));
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, checkIntent(c));
        }
    }

    private static void cancelCheck(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(checkIntent(c));
    }

    // ---------- shortcuts (long-press the Focus Guard icon, or a home-screen icon) ----------

    private static Intent launchIntent(Context c, String pkg) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("focusguard://bank/" + pkg))
                .setClassName(c.getPackageName(), BankLaunchActivity.class.getName());
    }

    private static ShortcutInfo shortcut(Context c, String pkg) {
        String label = Apps.label(c, pkg);
        ShortcutInfo.Builder b = new ShortcutInfo.Builder(c, "bank_" + pkg)
                .setShortLabel(label)
                .setLongLabel("Open " + label)
                .setIntent(launchIntent(c, pkg));
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            int size = Ui.dp(c, 48);
            Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            d.setBounds(0, 0, size, size);
            d.draw(cv);
            b.setIcon(Icon.createWithBitmap(bm));
        } catch (Exception e) {
            b.setIcon(Icon.createWithResource(c, R.drawable.ic_launcher));
        }
        return b.build();
    }

    public static void updateShortcuts(Context c) {
        if (Build.VERSION.SDK_INT < 25) return;
        ShortcutManager sm = c.getSystemService(ShortcutManager.class);
        if (sm == null) return;
        List<ShortcutInfo> list = new ArrayList<>();
        for (String p : apps(c)) {
            if (list.size() >= 2) break;
            try {
                list.add(shortcut(c, p));
            } catch (Exception ignored) {
            }
        }
        try {
            sm.setDynamicShortcuts(list);
        } catch (Exception ignored) {
        }
    }

    /** Puts an icon on the home screen that opens the banking app through Focus Guard. */
    public static void pinToHome(Context c, String pkg) {
        if (Build.VERSION.SDK_INT < 26) {
            Rules.toast(c, "Your phone doesn't support this. Use the Open button instead.");
            return;
        }
        ShortcutManager sm = c.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Rules.toast(c, "Your home screen doesn't support adding icons this way.");
            return;
        }
        try {
            sm.requestPinShortcut(shortcut(c, pkg), null);
        } catch (Exception e) {
            Rules.toast(c, "Couldn't add the icon.");
        }
    }

    public static String label(Context c) {
        String p = activeApp(c);
        return p == null ? "your banking app" : Apps.label(c, p);
    }

    @SuppressWarnings("unused")
    private static String lower(String s) { return s.toLowerCase(Locale.US); }
}
