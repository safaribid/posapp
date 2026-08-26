package com.safaribid.pos.printer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Saves / loads the last used Bluetooth printer (MAC + name).
 */
public final class PrinterPrefs {

    private static final String PREFS_NAME = "safaribid_printer_prefs";
    private static final String KEY_LAST_MAC = "last_printer_mac";
    private static final String KEY_LAST_NAME = "last_printer_name";

    private PrinterPrefs() {}

    public static void saveLastPrinter(Context context, String mac, String name) {
        if (mac == null || mac.trim().isEmpty()) return;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putString(KEY_LAST_MAC, mac.trim())
                .putString(KEY_LAST_NAME, name != null ? name.trim() : "")
                .apply();
    }

    public static String getLastMac(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_MAC, null);
    }

    public static String getLastName(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_NAME, "");
    }

    public static void clear(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_MAC)
                .remove(KEY_LAST_NAME)
                .apply();
    }
}