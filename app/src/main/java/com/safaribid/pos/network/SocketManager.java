package com.safaribid.pos.network;

import android.util.Log;

import com.safaribid.pos.utils.AppConfig;

import java.net.URISyntaxException;
import java.util.Collections;

import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketManager {

    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private Socket socket;
    private OrderListener orderListener;

    public interface OrderListener {
        void onNewOrder(String orderJson);
        void onOrderUpdated(String orderJson);
    }

    private SocketManager() {
    }

    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }

    public void setOrderListener(OrderListener listener) {
        this.orderListener = listener;
    }

    public void connect(String userId) {
        if (socket != null && socket.connected()) {
            return;
        }

        try {
            IO.Options options = new IO.Options();
            options.forceNew = true;
            options.reconnection = true;
            options.auth = Collections.singletonMap("userId", userId);

            // Adjust if your socket runs on a different URL/path
            String socketUrl = AppConfig.SERVER_API
                    .replace("/api/pos", "")
                    .replace("/api/pos/", "");

            socket = IO.socket(socketUrl, options);

            socket.on(Socket.EVENT_CONNECT, args -> Log.d(TAG, "Socket connected"));
            socket.on(Socket.EVENT_DISCONNECT, args -> Log.d(TAG, "Socket disconnected"));
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> Log.e(TAG, "Socket connect error"));

            // Temporary event names – update when backend confirms
            socket.on("new_order", args -> {
                if (args.length > 0 && orderListener != null) {
                    orderListener.onNewOrder(args[0].toString());
                }
            });

            socket.on("order_updated", args -> {
                if (args.length > 0 && orderListener != null) {
                    orderListener.onOrderUpdated(args[0].toString());
                }
            });

            socket.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Socket URI error", e);
        }
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }
}