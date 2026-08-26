package com.safaribid.pos.printer;

import android.graphics.Bitmap;

public interface IPrinter {

    interface PrintCallback {
        void onSuccess();
        void onError(String message);
    }

    interface ConnectionCallback {
        void onConnected();
        void onConnectionFailed(String error);
        void onDisconnected();
    }

    /** SM1 – no MAC needed */
    void connect(ConnectionCallback callback);

    /** Bluetooth – needs MAC */
    void connect(String macAddress, ConnectionCallback callback);

    boolean isConnected();

    void disconnect();

    /** Common print method used by both printers */
    void printBitmap(Bitmap bitmap, PrintCallback callback);
}