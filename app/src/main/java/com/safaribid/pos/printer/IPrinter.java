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

    /** Built-in SM1 or auto BT — no MAC argument */
    void connect(ConnectionCallback callback);

    /** Explicit MAC (after manual pick or saved address) */
    void connect(String macAddress, ConnectionCallback callback);

    boolean isConnected();

    void disconnect();

    void printBitmap(Bitmap bitmap, PrintCallback callback);
}
