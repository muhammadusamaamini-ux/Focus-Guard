package com.usama.focusguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Watches which app is on screen. During a focus session it sends you away from
 * blocked apps and blocked websites, and from the settings screens that could be
 * used to switch Focus Guard off or uninstall it.
 */
public class BlockerService extends AccessibilityService {

    /** Browser package -> id of its address bar. */
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

    /** Screens where Focus Guard could be turned off or uninstalled. */
    private static final Set<String> GUARDED = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.samsung.android.lool"
    ));

    private long lastBlock = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!Prefs.isActive(this)) return;

        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;

        if (Prefs.getApps(this).contains(pkg)) {
            block(false);
            return;
        }

        if (GUARDED.contains(pkg)) {
            if (screenMentionsFocusGuard()) block(false);
            return;
        }

        String[] barIds = BROWSER_BARS.get(pkg);
        if (barIds != null) {
            String url = readAddressBar(barIds);
            if (url != null && isBlockedSite(url)) block(true);
        }
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
        for (String site : Prefs.getSites(this)) {
            if (site.isEmpty()) continue;
            if (host.equals(site) || host.endsWith("." + site)) return true;
        }
        return false;
    }

    private boolean screenMentionsFocusGuard() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes =
                root.findAccessibilityNodeInfosByText(getString(R.string.app_name));
        return nodes != null && !nodes.isEmpty();
    }

    private void block(boolean fromBrowser) {
        long now = SystemClock.uptimeMillis();
        if (now - lastBlock < 1200L) return;
        lastBlock = now;

        performGlobalAction(fromBrowser ? GLOBAL_ACTION_BACK : GLOBAL_ACTION_HOME);

        Intent i = new Intent(this, BlockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {
        // Nothing to clean up.
    }
}
