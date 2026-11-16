package com.usama.focusguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wakes up every 15 seconds during banking mode, and after a restart, to switch the blocker back on. */
public class BankReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            if (Bank.active(c)) Bank.resume(c);
            return;
        }
        Bank.check(c);
    }
}
