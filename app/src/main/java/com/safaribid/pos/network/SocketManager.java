package com.safaribid.pos.network;

import android.util.Log;

import com.safaribid.pos.utils.AppConfig;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketManager {

    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private Socket socket;

    private SocketManager() {
    }

    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }

    public void connect(String token) {
        try {
            IO.Options options = new IO.Options();
            options.forceNew = true;
            options.reconnection = true;
            options.auth = java.util.Collections.singletonMap("token", token);

            // Adjust the socket URL if the backend uses a different path
            String socketUrl = AppConfig.SERVER_API.replace("/api/pos", "");
            socket = IO.socket(socketUrl, options);

            socket.on(Socket.EVENT_CONNECT, args -> Log.d(TAG, "Socket connected"));
            socket.on(Socket.EVENT_DISCONNECT, args -> Log.d(TAG, "Socket disconnected"));
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> Log.e(TAG, "Socket connect error"));

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

    public Socket getSocket() {
        return socket;
    }
}