package com.safaribid.pos.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives FCM while app is in background / killed (and data messages in foreground).
 * Backend should send a partial payload, e.g. orderId + title/body.
 */
public class PosFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "PosFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        // Token is also sent on next login via AuthManager.
        // Optional: POST token to backend if you add an update-token endpoint.
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        Log.d(TAG, "FCM from=" + message.getFrom());

        String title = null;
        String body = null;
        String orderId = null;

        // Notification payload (shown by system if app in background — we still handle data)
        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }

        Map<String, String> data = message.getData();
        if (data != null && !data.isEmpty()) {
            Log.d(TAG, "FCM data=" + data);

            if (data.containsKey("title") && title == null) {
                title = data.get("title");
            }
            if (data.containsKey("body") && body == null) {
                body = data.get("body");
            }
            if (data.containsKey("message") && body == null) {
                body = data.get("message");
            }

            // Common key names — backend may use any of these
            if (data.containsKey("orderId")) {
                orderId = data.get("orderId");
            } else if (data.containsKey("order_id")) {
                orderId = data.get("order_id");
            } else if (data.containsKey("id")) {
                orderId = data.get("id");
            }
        }

        // Always show our notification so tap opens the right screen
        NotificationHelper.showNewOrderNotification(
                getApplicationContext(),
                orderId,
                title,
                body
        );
    }
}