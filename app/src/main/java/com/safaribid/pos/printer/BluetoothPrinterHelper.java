package com.safaribid.pos.printer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.safaribid.pos.utils.PrintUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Built-in (or bonded) printer over Classic Bluetooth SPP.
 * connect() = auto: saved MAC → best bonded candidate.
 * connect(mac) = explicit address after manual selection.
 */
public class BluetoothPrinterHelper implements IPrinter {

    private static final String TAG = "BluetoothPrinterHelper";
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothSocket socket;
    private OutputStream outputStream;
    private boolean isConnected = false;
    private String connectedMac;
    private String connectedName;

    public BluetoothPrinterHelper(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /** Auto: last saved MAC, then ranked bonded devices. */
    @Override
    public void connect(ConnectionCallback callback) {
        executor.execute(() -> {
            if (bluetoothAdapter == null) {
                notifyFailed(callback, "Bluetooth not supported");
                return;
            }
            if (!bluetoothAdapter.isEnabled()) {
                notifyFailed(callback, "Please turn on Bluetooth");
                return;
            }

            // 1) Saved MAC on this device
            String lastMac = PrinterPrefs.getLastMac(context);
            if (lastMac != null && !lastMac.isEmpty()) {
                if (tryConnectBlocking(lastMac)) {
                    notifyConnected(callback);
                    return;
                }
                Log.w(TAG, "Saved MAC failed, trying auto-discover");
            }

            // 2) Ranked bonded candidates
            List<BluetoothDevice> candidates =
                    InternalPrinterFinder.rankedBondedPrinters(bluetoothAdapter);
            if (candidates.isEmpty()) {
                notifyFailed(callback,
                        "No paired Bluetooth devices. Pair the printer or select manually.");
                return;
            }

            for (BluetoothDevice device : candidates) {
                String mac = device.getAddress();
                if (mac == null) continue;
                if (tryConnectBlocking(mac)) {
                    String name;
                    try {
                        name = device.getName() != null ? device.getName() : "Printer";
                    } catch (SecurityException e) {
                        name = "Printer";
                    }
                    PrinterPrefs.saveLastPrinter(context, mac, name);
                    connectedName = name;
                    notifyConnected(callback);
                    return;
                }
            }

            notifyFailed(callback,
                    "Could not connect automatically. Select the printer manually.");
        });
    }

    @Override
    public void connect(String macAddress, ConnectionCallback callback) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            notifyFailed(callback, "MAC address is empty");
            return;
        }

        final String mac = macAddress.trim();
        executor.execute(() -> {
            if (tryConnectBlocking(mac)) {
                String name = PrinterPrefs.getLastName(context);
                if (name == null || name.isEmpty()) name = "Printer";
                PrinterPrefs.saveLastPrinter(context, mac, name);
                notifyConnected(callback);
            } else {
                notifyFailed(callback, "Connection failed for " + mac);
            }
        });
    }

    private boolean tryConnectBlocking(String mac) {
        try {
            closeQuietly();

            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mac);
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            outputStream = socket.getOutputStream();
            isConnected = true;
            connectedMac = mac;
            try {
                connectedName = device.getName() != null ? device.getName() : "Printer";
            } catch (SecurityException e) {
                connectedName = "Printer";
            }

            write(EscPosCommands.INIT);
            Log.d(TAG, "Connected to " + connectedName + " " + mac);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "tryConnect failed mac=" + mac, e);
            closeQuietly();
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected && socket != null && socket.isConnected();
    }

    @Override
    public void disconnect() {
        executor.execute(this::closeQuietly);
    }

    @Override
    public void printBitmap(Bitmap bitmap, PrintCallback callback) {
        if (!isConnected()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("Printer not connected");
            });
            return;
        }
        if (bitmap == null) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("Receipt image is empty");
            });
            return;
        }

        executor.execute(() -> {
            try {
                byte[] imageData = PrintUtil.getBitmapData(bitmap);
                int widthBytes = PrintUtil.getPaddingBitWidth(
                        Math.min(bitmap.getWidth(), PrintUtil.MAX_BIT_WIDTH)) / 8;
                int height = bitmap.getHeight();

                write(EscPosCommands.ALIGN_CENTER);
                write(EscPosCommands.rasterHeader(widthBytes, height));
                write(imageData);
                write("\n\n".getBytes());
                write(EscPosCommands.PARTIAL_CUT);

                // Remember successful printer on this device
                if (connectedMac != null) {
                    PrinterPrefs.saveLastPrinter(context, connectedMac, connectedName);
                }

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess();
                });
            } catch (Exception e) {
                Log.e(TAG, "printBitmap failed", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Print failed");
                    }
                });
            }
        });
    }

    private void write(byte[] data) throws IOException {
        if (outputStream == null) throw new IOException("OutputStream is null");
        outputStream.write(data);
        outputStream.flush();
    }

    private void closeQuietly() {
        isConnected = false;
        connectedMac = null;
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        outputStream = null;
        socket = null;
    }

    private void notifyConnected(ConnectionCallback callback) {
        mainHandler.post(() -> {
            if (callback != null) callback.onConnected();
        });
    }

    private void notifyFailed(ConnectionCallback callback, String error) {
        mainHandler.post(() -> {
            if (callback != null) callback.onConnectionFailed(error);
        });
    }
}
