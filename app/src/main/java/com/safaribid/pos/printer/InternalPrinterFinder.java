package com.safaribid.pos.printer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the likely built-in BT printer among bonded devices.
 * No hardcoded MAC — works across different units of the same architecture.
 */
public final class InternalPrinterFinder {

    private static final String TAG = "InternalPrinterFinder";

    private InternalPrinterFinder() {
    }

    /**
     * Rank bonded devices; highest score first.
     * Returns empty list if none / BT unavailable.
     */
    public static List<BluetoothDevice> rankedBondedPrinters(BluetoothAdapter adapter) {
        List<BluetoothDevice> result = new ArrayList<>();
        if (adapter == null) return result;

        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) return result;

            List<Scored> scored = new ArrayList<>();
            for (BluetoothDevice d : bonded) {
                if (d == null || d.getAddress() == null) continue;
                scored.add(new Scored(d, score(d)));
            }

            Collections.sort(scored, new Comparator<Scored>() {
                @Override
                public int compare(Scored a, Scored b) {
                    return Integer.compare(b.score, a.score);
                }
            });

            for (Scored s : scored) {
                Log.d(TAG, "candidate score=" + s.score
                        + " name=" + safeName(s.device)
                        + " mac=" + s.device.getAddress());
                result.add(s.device);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing Bluetooth permission", e);
        }
        return result;
    }

    /** Best guess, or null. */
    public static BluetoothDevice bestCandidate(BluetoothAdapter adapter) {
        List<BluetoothDevice> list = rankedBondedPrinters(adapter);
        if (list.isEmpty()) return null;
        // Prefer score > 0; if only unknown devices, still try the first
        return list.get(0);
    }

    private static int score(BluetoothDevice device) {
        String name = safeName(device).toLowerCase(Locale.US);
        int score = 0;

        if (name.contains("innerprinter") || name.contains("inner printer")) score += 100;
        if (name.contains("inner")) score += 40;
        if (name.contains("printer")) score += 50;
        if (name.contains("thermal")) score += 30;
        if (name.contains("receipt")) score += 25;
        if (name.contains("pos")) score += 20;
        if (name.contains("print")) score += 15;
        if (name.contains("blue")) score += 5;

        // Unknown name still usable when it's the only bonded device
        if (name.isEmpty() || name.equals("unknown")) score += 1;

        return score;
    }

    private static String safeName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n != null ? n : "";
        } catch (SecurityException e) {
            return "";
        }
    }

    private static final class Scored {
        final BluetoothDevice device;
        final int score;

        Scored(BluetoothDevice device, int score) {
            this.device = device;
            this.score = score;
        }
    }
}
