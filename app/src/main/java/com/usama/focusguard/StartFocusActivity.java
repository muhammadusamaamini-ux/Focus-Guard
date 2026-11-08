package com.usama.focusguard;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

/** Invisible screen used by the home-screen shortcuts (long-press the app icon). */
public class StartFocusActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int minutes = 0;
        Uri data = getIntent() == null ? null : getIntent().getData();
        if (data != null) {
            try {
                minutes = Integer.parseInt(data.getLastPathSegment());
            } catch (Exception ignored) {
            }
        }
        if (minutes > 0) Rules.startFocus(this, minutes);
        finish();
    }
}
