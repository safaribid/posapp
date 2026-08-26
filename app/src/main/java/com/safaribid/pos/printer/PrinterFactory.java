package com.safaribid.pos.printer;

import android.content.Context;

public class PrinterFactory {

    public enum Type {
        BUILT_IN_SM1,
        EXTERNAL_BLUETOOTH
    }

    private static final String PREFS = "safaribid_printer_prefs";
    private static final String KEY_TYPE = "preferred_printer_type";

    public static IPrinter create(Context context, Type type) {
        if (type == Type.BUILT_IN_SM1) {
            return new Sm1PrinterHelper(context);
        }
        return new BluetoothPrinterHelper(context);
    }

    public static IPrinter createDefault(Context context) {
        return create(context, getPreferredType(context));
    }

    public static void setPreferredType(Context context, Type type) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TYPE, type.name())
                .apply();
    }

    public static Type getPreferredType(Context context) {
        String saved = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TYPE, Type.BUILT_IN_SM1.name()); // default to SM1 if you prefer
        try {
            return Type.valueOf(saved);
        } catch (Exception e) {
            return Type.BUILT_IN_SM1;
        }
    }
}