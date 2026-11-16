package com.usama.focusguard;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** Invisible screen behind the banking-app shortcuts: switches the blocker off, then opens the bank app. */
public class BankLaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri data = getIntent() == null ? null : getIntent().getData();
        String pkg = data == null ? null : data.getLastPathSegment();
        if (pkg == null || !Bank.apps(this).contains(pkg)) {
            Rules.toast(this, "This banking app isn't in Focus Guard any more.");
            finish();
            return;
        }
        Bank.open(this, pkg, this::finish);
    }
}
