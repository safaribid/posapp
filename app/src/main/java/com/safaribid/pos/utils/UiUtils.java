package com.safaribid.pos.utils;

import android.app.Activity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import com.safaribid.pos.R;

public class UiUtils {

    /**
     * Configures the activity window to not use edge-to-edge enforcement on Android 15+.
     * Also sets the status bar color to brand_green and light icons to false.
     */
    public static void applyNonEdgeToEdge(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), true);
        activity.getWindow().setStatusBarColor(
                ContextCompat.getColor(activity, R.color.brand_green));
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
    }
}
