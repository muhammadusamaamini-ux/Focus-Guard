package com.usama.focusguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;

/** The Focus Guard notification: start buttons when idle, a countdown while blocking. */
public final class Notifier {

    private Notifier() {}

    private static final String CHANNEL = "status";
    private static final int ID_STATUS = 1;
    private static final int ID_WEEK = 2;
    private static final int[] QUICK = {30, 45, 60};

    private static String lastSig = "";

    public static void update(Context c) { update(c, false); }

    public static synchronized void update(Context c, boolean force) {
        String sig = signature(c);
        if (!force && sig.equals(lastSig)) return;
        lastSig = sig;

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannel(nm);

        Notification.Builder b = builder(c)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(openApp(c));

        if (Rules.emergencyOn(c)) {
            b.setContentTitle("Emergency time")
                    .setContentText("Blocking comes back on automatically.");
            countdown(b, Prefs.getLong(c, "em_until"));
        } else if (Rules.focusOn(c)) {
            b.setContentTitle("Focus is on")
                    .setContentText("Distracting apps and websites are blocked.");
            countdown(b, Prefs.getEnd(c));
        } else if (Rules.sleepNow(c)) {
            b.setContentTitle("Bedtime 🌙")
                    .setContentText("Distracting apps are blocked until "
                            + Prefs.clock(c, Prefs.sleepEnd(c)) + ".");
            countdown(b, Rules.sleepEndsAt(c));
        } else {
            b.setContentTitle("Ready to focus")
                    .setContentText("Tap a time below to start a focus session.");
            for (int m : QUICK) b.addAction(startAction(c, m));
        }

        try {
            nm.notify(ID_STATUS, b.build());
        } catch (SecurityException ignored) {
            // Notifications not allowed yet.
        }
    }

    public static void weekOver(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannel(nm);
        Notification n = builder(c)
                .setContentTitle("Your weekly plan has ended")
                .setContentText("Open Focus Guard to keep the same limits or change them.")
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .build();
        try {
            nm.notify(ID_WEEK, n);
        } catch (SecurityException ignored) {
        }
    }

    private static String signature(Context c) {
        if (Rules.emergencyOn(c)) return "em" + Prefs.getLong(c, "em_until");
        if (Rules.focusOn(c)) return "focus" + Prefs.getEnd(c);
        if (Rules.sleepNow(c)) return "sleep" + Rules.sleepEndsAt(c);
        return "idle";
    }

    private static Notification.Builder builder(Context c) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c).setPriority(Notification.PRIORITY_LOW);
        return b.setSmallIcon(R.drawable.ic_tile).setColor(Color.parseColor(Ui.INK));
    }

    private static void countdown(Notification.Builder b, long when) {
        b.setWhen(when).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true);
    }

    private static void ensureChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26) return;
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Focus status",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Notification.Action startAction(Context c, int minutes) {
        Intent i = new Intent(c, ActionReceiver.class).putExtra("minutes", minutes);
        PendingIntent pi = PendingIntent.getBroadcast(c, minutes, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(
                Icon.createWithResource(c, R.drawable.ic_tile), minutes + " min", pi).build();
    }
}
