package com.usama.focusguard;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Watches which app is on screen. It blocks distracting apps and websites during focus
 * time and bedtime, enforces daily limits, counts how long limited apps are used, and
 * protects Focus Guard's own settings while anything is locked.
 */
public class BlockerService extends AccessibilityService {

    private static final Map<String, String[]> BROWSER_BARS = new HashMap<>();

    static {
        BROWSER_BARS.put("com.android.chrome", new String[]{"com.android.chrome:id/url_bar"});
        BROWSER_BARS.put("com.chrome.beta", new String[]{"com.chrome.beta:id/url_bar"});
        BROWSER_BARS.put("com.microsoft.emmx", new String[]{"com.microsoft.emmx:id/url_bar"});
        BROWSER_BARS.put("com.brave.browser", new String[]{"com.brave.browser:id/url_bar"});
        BROWSER_BARS.put("com.kiwibrowser.browser", new String[]{"com.kiwibrowser.browser:id/url_bar"});
        BROWSER_BARS.put("com.vivaldi.browser", new String[]{"com.vivaldi.browser:id/url_bar"});
        BROWSER_BARS.put("com.opera.browser", new String[]{"com.opera.browser:id/url_field"});
        BROWSER_BARS.put("com.opera.mini.native", new String[]{"com.opera.mini.native:id/url_field"});
        BROWSER_BARS.put("com.sec.android.app.sbrowser",
                new String[]{"com.sec.android.app.sbrowser:id/location_bar_edit_text"});
        BROWSER_BARS.put("org.mozilla.firefox",
                new String[]{"org.mozilla.firefox:id/mozac_browser_toolbar_url_view"});
        BROWSER_BARS.put("com.duckduckgo.mobile.android",
                new String[]{"com.duckduckgo.mobile.android:id/omnibarTextInput"});
    }

    /** Settings apps where Focus Guard could be switched off. */
    private static final Set<String> SETTINGS = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.samsung.android.lool"
    ));

    /** Install/uninstall dialogs. Updates are allowed; uninstalling is not. */
    private static final Set<String> INSTALLERS = new HashSet<>(Arrays.asList(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
    ));

    private static final String[] APP_INFO_WORDS = {
            "Force stop", "Uninstall", "Clear storage", "Clear data", "Clear cache"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> ignored = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    private String warnedDay = "";
    private String lastPkg;
    private long lastTickAt;
    private long lastBlock;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long next = 15_000L;
            try {
                next = onTick();
            } catch (Exception ignored2) {
                // Never let one bad tick stop the blocker.
            }
            handler.postDelayed(this, next);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ignored.add("com.android.systemui");
        ignored.add(getPackageName());
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo i : imm.getEnabledInputMethodList()) ignored.add(i.getPackageName());
            }
        } catch (Exception ignored2) {
        }
        lastTickAt = SystemClock.elapsedRealtime();
        handler.removeCallbacks(tick);
        handler.post(tick);
        Notifier.update(this, true);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!ignored.contains(pkg)) lastPkg = pkg;
            Gray.apply(this, grayKey(pkg));
        }
        evaluate(pkg);
    }

    /** The app that grayscale should look at: null for the notification shade and keyboard. */
    private String grayKey(String pkg) {
        if (pkg == null) return null;
        if (pkg.equals(getPackageName())) return pkg;   // Focus Guard's own screens are never gray in focus
        return ignored.contains(pkg) ? null : pkg;
    }

    /** Counts usage of limited apps and re-checks the screen. Returns ms until the next tick. */
    private long onTick() {
        long nowUp = SystemClock.elapsedRealtime();
        long delta = Math.min(nowUp - lastTickAt, 5_000L);
        lastTickAt = nowUp;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean screenOn = pm != null && pm.isInteractive() && (km == null || !km.isKeyguardLocked());

        String onScreen = null;
        if (screenOn) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                onScreen = grayKey(root.getPackageName().toString());
            }
            String pkg = activePackage();
            if (pkg != null) {
                Integer lim = Rules.activeLimits(this).get(pkg);
                if (lim != null && delta > 0) {
                    long used = Prefs.addUsage(this, pkg, delta);
                    warnIfNearLimit(pkg, used, lim);
                }
                evaluate(pkg);
            }
        }

        Notifier.update(this);
        Gray.apply(this, onScreen);
        Dnd.apply(this);
        if (Rules.weekOver(this)) {
            long lock = Prefs.getLong(this, "plan_lock");
            if (Prefs.getLong(this, "week_notified") != lock) {
                Prefs.putLong(this, "week_notified", lock);
                Notifier.weekOver(this);
            }
        }
        return screenOn ? 3_000L : 15_000L;
    }

    private String activePackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) {
            String p = root.getPackageName().toString();
            return ignored.contains(p) ? null : p;
        }
        return lastPkg;
    }

    private void warnIfNearLimit(String pkg, long used, int limitMin) {
        String today = Prefs.today();
        if (!today.equals(warnedDay)) {
            warned.clear();
            warnedDay = today;
        }
        long limit = limitMin * Rules.MIN;
        if (used >= limit - Rules.MIN && used < limit && warned.add(pkg)) {
            Toast.makeText(this, "⏳ " + Apps.label(this, pkg) + ": 1 minute left today",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void evaluate(String pkg) {
        if (pkg == null || pkg.equals(getPackageName())) return;

        // 1. Protect Focus Guard itself while anything is locked.
        if (SETTINGS.contains(pkg) || INSTALLERS.contains(pkg)) {
            if (Rules.guardOn(this) && guardHit(pkg)) block("guard", pkg, false);
            return;
        }
        if (ignored.contains(pkg)) return;

        // 2. Daily limits (always on, emergencies don't apply).
        Integer lim = Rules.activeLimits(this).get(pkg);
        if (lim != null && Prefs.getUsage(this, pkg) >= lim * Rules.MIN) {
            block("limit", pkg, false);
            return;
        }

        // 3. Focus time and bedtime.
        String mode = Rules.mode(this);
        if (mode == null || Rules.emergencyOn(this)) return;
        if (Rules.blockedApps(this).contains(pkg)) {
            block(mode, pkg, false);
            return;
        }
        String[] barIds = BROWSER_BARS.get(pkg);
        if (barIds != null) {
            String url = readAddressBar(barIds);
            if (url != null && isBlockedSite(url)) block(mode, pkg, true);
        }
    }

    private boolean guardHit(String pkg) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        String name = getString(R.string.app_name);
        if (INSTALLERS.contains(pkg)) return has(root, name) && has(root, "uninstall");

        if (has(root, "Focus Guard uses this to see")) return true;   // its Accessibility page
        if (has(root, "Stop " + name) || has(root, "Turn off " + name)) return true;
        if (has(root, name)) {
            for (String w : APP_INFO_WORDS) if (has(root, w)) return true;   // its App info page
        }
        return false;
    }

    private static boolean has(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> n = root.findAccessibilityNodeInfosByText(text);
        return n != null && !n.isEmpty();
    }

    private String readAddressBar(String[] ids) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                CharSequence text = nodes.get(0).getText();
                if (text != null) return text.toString();
            }
        }
        return null;
    }

    private boolean isBlockedSite(String addressText) {
        String host = Prefs.cleanDomain(addressText);
        if (host.isEmpty()) return false;
        for (String site : Rules.blockedSites(this)) {
            if (site.isEmpty()) continue;
            if (host.equals(site) || host.endsWith("." + site)) return true;
        }
        return false;
    }

    private void block(String reason, String pkg, boolean fromBrowser) {
        long now = SystemClock.uptimeMillis();
        if (now - lastBlock < 1200L) return;
        lastBlock = now;

        performGlobalAction(fromBrowser ? GLOBAL_ACTION_BACK : GLOBAL_ACTION_HOME);

        Intent i = new Intent(this, BlockActivity.class)
                .putExtra("reason", reason)
                .putExtra("pkg", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
