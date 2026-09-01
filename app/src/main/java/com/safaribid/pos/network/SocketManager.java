package com.safaribid.pos.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.safaribid.pos.utils.AppConfig;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Socket foundation (P1):
 * - Connect with auth.uid
 * - Emit "register" after connect (and on reconnect)
 * - Optional ping / registrationSuccess
 *
 * Business events (order_request, delivery_status) are wired as hooks for P2/P3.
 */
public class SocketManager {

    private static final String TAG = "SocketManager";
    private static final long PING_INTERVAL_MS = 45_000L;

    private static SocketManager instance;

    private Socket socket;
    private String uid;
    private boolean intentionallyDisconnected = false;

    private ConnectionListener connectionListener;
    private OrderListener orderListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler pingHandler = new Handler(Looper.getMainLooper());
    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            if (socket != null && socket.connected()) {
                socket.emit("ping");
                Log.d(TAG, "ping sent");
                pingHandler.postDelayed(this, PING_INTERVAL_MS);
            }
        }
    };

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onRegistered(String uid);
        void onError(String message);
    }

    public interface OrderListener {
        /** P2: full order JSON from "order_request" */
        void onOrderRequest(String orderJson);

        /** P3: delivery_status payload JSON */
        void onDeliveryStatus(String payloadJson);
    }

    private SocketManager() {
    }

    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void setOrderListener(OrderListener listener) {
        this.orderListener = listener;
    }

    /**
     * Connect and register as the business owner UID.
     * Safe to call multiple times; reconnects if needed.
     */
    public void connect(String userUid) {
        if (userUid == null || userUid.trim().isEmpty()) {
            Log.e(TAG, "connect: uid is empty");
            notifyError("User uid is required for socket");
            return;
        }

        this.uid = userUid.trim();
        this.intentionallyDisconnected = false;

        if (socket != null && socket.connected()) {
            // Already connected — ensure we are registered for this uid
            emitRegister();
            return;
        }

        // Tear down any half-open socket
        if (socket != null) {
            socket.off();
            socket.disconnect();
            socket = null;
        }

        try {
            IO.Options options = new IO.Options();
            options.forceNew = true;
            options.reconnection = true;
            options.reconnectionAttempts = Integer.MAX_VALUE;
            options.reconnectionDelay = 1000;
            options.reconnectionDelayMax = 10000;
            options.timeout = 20000;

            // CRITICAL: backend only accepts websocket
            options.transports = new String[]{"websocket"};

            // Backend: handshake.auth.uid
            Map<String, String> auth = new HashMap<>();
            auth.put("uid", this.uid);
            options.auth = auth;

            String socketUrl = buildSocketUrl(AppConfig.SERVER_API);
            Log.d(TAG, "Connecting to " + socketUrl + " as uid=" + this.uid);

            socket = IO.socket(socketUrl, options);
            attachCoreListeners();
            // Business events ready for P2/P3 (no-op until listener set)
            attachBusinessListeners();

            socket.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid socket URL", e);
            notifyError("Invalid socket URL: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Socket connect failed", e);
            notifyError("Socket connect failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        intentionallyDisconnected = true;
        stopPing();
        uid = null;

        if (socket != null) {
            try {
                socket.off();
                socket.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "disconnect error", e);
            }
            socket = null;
        }
        Log.d(TAG, "Socket disconnected intentionally");
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public String getUid() {
        return uid;
    }

    // ------------------------------------------------------------------
    // Core listeners
    // ------------------------------------------------------------------

    private void attachCoreListeners() {
        socket.on(Socket.EVENT_CONNECT, onConnect);
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        socket.on("registrationSuccess", onRegistrationSuccess);
    }

    private final Emitter.Listener onConnect = args -> {
        Log.d(TAG, "EVENT_CONNECT");
        emitRegister();
        startPing();
        mainHandler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onConnected();
            }
        });
    };

    private final Emitter.Listener onDisconnect = args -> {
        String reason = args != null && args.length > 0 ? String.valueOf(args[0]) : "";
        Log.d(TAG, "EVENT_DISCONNECT reason=" + reason);
        stopPing();
        mainHandler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onDisconnected();
            }
        });
        // Library will reconnect if not intentional; on CONNECT we register again
    };

    private final Emitter.Listener onConnectError = args -> {
        final String msg = (args != null && args.length > 0 && args[0] != null)
                ? args[0].toString()
                : "connect_error";
        Log.e(TAG, "EVENT_CONNECT_ERROR: " + msg);
        mainHandler.post(() -> notifyError(msg));
    };

    private final Emitter.Listener onRegistrationSuccess = args -> {
        Log.d(TAG, "registrationSuccess: " + (args.length > 0 ? args[0] : ""));
        mainHandler.post(() -> {
            if (connectionListener != null && uid != null) {
                connectionListener.onRegistered(uid);
            }
        });
    };

    private void emitRegister() {
        if (socket == null || uid == null || uid.isEmpty()) return;
        socket.emit("register", uid);
        Log.d(TAG, "emitted register uid=" + uid);
    }

    // ------------------------------------------------------------------
    // Business listeners (hooks for P2 / P3)
    // ------------------------------------------------------------------

    private void attachBusinessListeners() {
        socket.on("order_request", args -> {
            final String json = firstArgToString(args);
            Log.d(TAG, "order_request: " + json);
            mainHandler.post(() -> {
                if (orderListener != null && json != null) {
                    orderListener.onOrderRequest(json);
                }
            });
        });

        socket.on("delivery_status", args -> {
            final String json = firstArgToString(args);
            Log.d(TAG, "delivery_status: " + json);
            mainHandler.post(() -> {
                if (orderListener != null && json != null) {
                    orderListener.onDeliveryStatus(json);
                }
            });
        });
    }

    private static String firstArgToString(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return null;
        Object a = args[0];
        if (a instanceof JSONObject) {
            return a.toString();
        }
        return String.valueOf(a);
    }

    // ------------------------------------------------------------------
    // Ping
    // ------------------------------------------------------------------

    private void startPing() {
        stopPing();
        pingHandler.postDelayed(pingRunnable, PING_INTERVAL_MS);
    }

    private void stopPing() {
        pingHandler.removeCallbacks(pingRunnable);
    }

    // ------------------------------------------------------------------
    // URL helper
    // ------------------------------------------------------------------

    /**
     * Socket.IO connects to the server origin, not the REST path.
     * Examples:
     *   https://api.safaribid.com/api/pos  → https://api.safaribid.com
     *   https://api.safaribid.com/api/pos/ → https://api.safaribid.com
     */
    static String buildSocketUrl(String serverApi) {
        if (serverApi == null || serverApi.trim().isEmpty()) {
            return "https://api.safaribid.com";
        }
        String url = serverApi.trim();
        // Strip trailing slash
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // Strip known API prefixes
        if (url.endsWith("/api/pos")) {
            url = url.substring(0, url.length() - "/api/pos".length());
        } else if (url.endsWith("/api")) {
            url = url.substring(0, url.length() - "/api".length());
        }
        return url;
    }

    private void notifyError(String message) {
        if (connectionListener != null) {
            connectionListener.onError(message);
        }
    }
}