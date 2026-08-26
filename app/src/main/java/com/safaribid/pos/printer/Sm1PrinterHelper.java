package com.safaribid.pos.printer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.android.sdk.api.ISdkServiceManager;
import com.android.sdk.api.constant.ServiceID;
import com.android.sdk.api.printer.IPrinterBinderService;
import com.android.sdk.api.printer.IPrinterCallback;
import com.safaribid.pos.utils.PrintUtil;

public class Sm1PrinterHelper implements IPrinter {

    private static final String TAG = "Sm1PrinterHelper";

    private final Context context;
    private IPrinterBinderService printerService;
    private boolean bound = false;

    private PrintCallback pendingCallback;
    private byte[] pendingData;
    private ConnectionCallback pendingConnectionCallback;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                ISdkServiceManager manager = ISdkServiceManager.Stub.asInterface(service);
                Object binder = manager.getService(ServiceID.SERVICE_ID_PRINTER);
                printerService = (IPrinterBinderService) binder;
                bound = true;

                if (pendingConnectionCallback != null) {
                    pendingConnectionCallback.onConnected();
                    pendingConnectionCallback = null;
                }

                if (pendingData != null) {
                    doPrint(pendingData, pendingCallback);
                    pendingData = null;
                    pendingCallback = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Printer bind error", e);
                if (pendingConnectionCallback != null) {
                    pendingConnectionCallback.onConnectionFailed("Printer bind failed: " + e.getMessage());
                    pendingConnectionCallback = null;
                }
                if (pendingCallback != null) {
                    pendingCallback.onError("Printer bind failed: " + e.getMessage());
                    pendingCallback = null;
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            printerService = null;
            bound = false;
        }
    };

    public Sm1PrinterHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void connect(ConnectionCallback callback) {
        if (bound && printerService != null) {
            if (callback != null) callback.onConnected();
            return;
        }
        pendingConnectionCallback = callback;
        bind();
    }

    @Override
    public void connect(String macAddress, ConnectionCallback callback) {
        // SM1 ignores MAC
        connect(callback);
    }

    @Override
    public boolean isConnected() {
        return bound && printerService != null;
    }

    @Override
    public void disconnect() {
        unbind();
    }

    @Override
    public void printBitmap(Bitmap bitmap, PrintCallback callback) {
        if (bitmap == null) {
            if (callback != null) callback.onError("Receipt image is empty");
            return;
        }

        try {
            byte[] data = PrintUtil.getBitmapData(bitmap);

            if (!bound || printerService == null) {
                pendingData = data;
                pendingCallback = callback;
                bind();
                Toast.makeText(context, "Connecting to printer...", Toast.LENGTH_SHORT).show();
                return;
            }

            doPrint(data, callback);
        } catch (Exception e) {
            if (callback != null) callback.onError("Print prepare failed: " + e.getMessage());
        }
    }

    private void bind() {
        try {
            Intent intent = new Intent();
            intent.setAction("com.android.sdk.api.SdkService");
            intent.setPackage("com.android.sdk");
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "bindService failed", e);
            if (pendingConnectionCallback != null) {
                pendingConnectionCallback.onConnectionFailed("bindService failed: " + e.getMessage());
                pendingConnectionCallback = null;
            }
        }
    }

    private void unbind() {
        if (bound) {
            try {
                context.unbindService(connection);
            } catch (Exception ignored) {
            }
            bound = false;
            printerService = null;
        }
    }

    private void doPrint(byte[] data, PrintCallback callback) {
        try {
            printerService.printBitmap(0, data, 0, 0, new IPrinterCallback.Stub() {
                @Override
                public void onResult(int resultCode, String message, int extra) throws RemoteException {
                    if (resultCode == 0) {
                        if (callback != null) callback.onSuccess();
                    } else {
                        if (callback != null) {
                            callback.onError("Print failed: " + resultCode + " - " + message);
                        }
                    }
                }
            });
        } catch (Exception e) {
            if (callback != null) callback.onError("Print error: " + e.getMessage());
        }
    }
}