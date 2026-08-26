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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothPrinterHelper implements IPrinter {

    private static final String TAG = "BluetoothPrinterHelper";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothSocket socket;
    private OutputStream outputStream;
    private boolean isConnected = false;

    public BluetoothPrinterHelper(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public void connect(ConnectionCallback callback) {
        notifyFailed(callback, "MAC address required for Bluetooth printer");
    }

    @Override
    public void connect(String macAddress, ConnectionCallback callback) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            notifyFailed(callback, "MAC address is empty");
            return;
        }

        executor.execute(() -> {
            try {
                closeQuietly();

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress.trim());
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                outputStream = socket.getOutputStream();
                isConnected = true;

                write(EscPosCommands.INIT);

                mainHandler.post(() -> {
                    if (callback != null) callback.onConnected();
                });
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                closeQuietly();
                notifyFailed(callback, e.getMessage() != null ? e.getMessage() : "Connection failed");
            }
        });
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
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Printer not connected"));
            }
            return;
        }
        if (bitmap == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Receipt image is empty"));
            }
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

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess();
                });
            } catch (Exception e) {
                Log.e(TAG, "printBitmap failed", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
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
        try { if (outputStream != null) outputStream.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        outputStream = null;
        socket = null;
    }

    private void notifyFailed(ConnectionCallback callback, String error) {
        mainHandler.post(() -> {
            if (callback != null) callback.onConnectionFailed(error);
        });
    }
}