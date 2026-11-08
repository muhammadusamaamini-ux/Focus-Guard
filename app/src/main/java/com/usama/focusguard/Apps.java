package com.usama.focusguard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lists the apps installed on the phone. */
public final class Apps {

    private Apps() {}

    public static class Item {
        public String label;
        public String pkg;
        public Drawable icon;
    }

    /** Slow: call from a background thread. */
    public static List<Item> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(launcher, 0);
        List<Item> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            Item it = new Item();
            it.pkg = pkg;
            it.label = String.valueOf(ri.loadLabel(pm));
            try {
                it.icon = ri.loadIcon(pm);
            } catch (Exception e) {
                it.icon = null;
            }
            items.add(it);
        }
        items.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return items;
    }

    public static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }
}
