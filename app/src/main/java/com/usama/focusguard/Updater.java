package com.usama.focusguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** "Check for updates": looks at the newest GitHub release and installs it in place. */
public final class Updater {

    private Updater() {}

    private static final String LATEST =
            "https://api.github.com/repos/muhammadusamaamini-ux/Focus-Guard/releases/latest";

    public static int installedBuild(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void check(Activity a) {
        AlertDialog wait = new AlertDialog.Builder(a)
                .setMessage("Checking for updates…").setCancelable(false).show();
        new Thread(() -> {
            String error = null;
            int latest = 0;
            String apkUrl = null;
            try {
                HttpURLConnection con = open(LATEST);
                con.setRequestProperty("Accept", "application/vnd.github+json");
                int code = con.getResponseCode();
                if (code == 404) {
                    error = "Couldn't find the updates on GitHub. Make sure your Focus-Guard repository "
                            + "is set to Public.";
                } else if (code != 200) {
                    error = "GitHub answered with error " + code + ". Please try again later.";
                } else {
                    JSONObject j = new JSONObject(readAll(con.getInputStream()));
                    latest = buildNumber(j.optString("tag_name"));
                    JSONArray assets = j.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject as = assets.getJSONObject(i);
                            if (as.optString("name").endsWith(".apk")) {
                                apkUrl = as.optString("browser_download_url");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                error = "Couldn't reach GitHub. Check your internet connection and try again.";
            } catch (Exception e) {
                error = "Couldn't read the update information.";
            }

            final String err = error;
            final int newest = latest;
            final String url = apkUrl;
            a.runOnUiThread(() -> {
                wait.dismiss();
                if (a.isFinishing()) return;
                int have = installedBuild(a);
                if (err != null) {
                    message(a, "Update check failed", err);
                } else if (newest <= have) {
                    message(a, "You're up to date", "You have the newest version (build " + have + ").");
                } else if (url == null || url.isEmpty()) {
                    message(a, "Update not ready", "Build " + newest + " is still being prepared. "
                            + "Try again in a few minutes.");
                } else {
                    new AlertDialog.Builder(a)
                            .setTitle("Update available")
                            .setMessage("Build " + newest + " is ready (you have build " + have + "). "
                                    + "Download and install it now? Your settings are kept.")
                            .setPositiveButton("Update", (d, w) -> download(a, url, newest))
                            .setNegativeButton("Later", null)
                            .show();
                }
            });
        }).start();
    }

    private static void download(Activity a, String url, int build) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("One-time permission")
                    .setMessage("Allow Focus Guard to install its own updates: on the next screen, turn on "
                            + "\"Allow from this source\". Then come back and tap Check for updates again.")
                    .setPositiveButton("Open settings", (d, w) -> a.startActivity(
                            new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName()))))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        AlertDialog progress = new AlertDialog.Builder(a)
                .setTitle("Downloading build " + build)
                .setMessage("Please wait…")
                .setCancelable(false)
                .show();

        new Thread(() -> {
            String error = null;
            PackageInstaller.Session session = null;
            try {
                PackageInstaller installer = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = installer.createSession(params);
                session = installer.openSession(id);

                HttpURLConnection con = open(url);
                if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
                try (InputStream in = con.getInputStream();
                     OutputStream out = session.openWrite("FocusGuard.apk", 0, -1)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    session.fsync(out);
                }

                Intent done = new Intent(a, InstallReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pi = PendingIntent.getBroadcast(a, id, done, flags);
                session.commit(pi.getIntentSender());
                session.close();
                session = null;
            } catch (Exception e) {
                error = "The download didn't finish. Check your internet connection and try again.";
                if (session != null) {
                    try {
                        session.abandon();
                    } catch (Exception ignored) {
                    }
                }
            }
            final String err = error;
            a.runOnUiThread(() -> {
                progress.dismiss();
                if (err != null && !a.isFinishing()) message(a, "Update failed", err);
            });
        }).start();
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "FocusGuard-Android");
        return con;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    /** "build-12" -> 12 */
    private static int buildNumber(String tag) {
        StringBuilder d = new StringBuilder();
        for (char ch : tag.toCharArray()) if (Character.isDigit(ch)) d.append(ch);
        try {
            return d.length() == 0 ? 0 : Integer.parseInt(d.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void message(Activity a, String title, String msg) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }
}
