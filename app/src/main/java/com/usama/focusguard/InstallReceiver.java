package com.usama.focusguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Gets the result of an in-app update and shows Android's "Update this app?" screen. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                } catch (Exception e) {
                    Rules.toast(c, "Open Focus Guard and tap Check for updates again.");
                }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Rules.toast(c, "Focus Guard is updated.");
        } else if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Rules.toast(c, "The update didn't install" + (msg == null ? "" : " (" + msg + ")")
                    + ". If Play Protect blocked it, pause Play Protect and try again.");
        }
    }
}
