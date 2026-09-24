package org.tiberiandawn.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

/** First-run help for the native mobile touch gestures. */
public final class MobileTouchGuide {
    private static final String PREFS = "mobile_touch_guide";
    private static final String SHOWN_V2 = "shown_v2";

    private MobileTouchGuide() {}

    public static void showOnce(Activity activity, String title, String message,
                                String positiveLabel) {
        SharedPreferences prefs =
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(SHOWN_V2, false)) return;
        show(activity, title, message, positiveLabel);
        prefs.edit().putBoolean(SHOWN_V2, true).apply();
    }

    public static void show(Activity activity, String title, String message,
                            String positiveLabel) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveLabel, null)
            .show();
    }
}
