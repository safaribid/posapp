package com.safaribid.pos.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.safaribid.pos.R;
import com.safaribid.pos.auth.LoginActivity;
import com.safaribid.pos.ui.orders.OrderDetailActivity;
import com.safaribid.pos.ui.orders.OrdersActivity;

public final class NotificationHelper {

    public static final String CHANNEL_ORDERS = "orders_v1";
    public static final String EXTRA_ORDER_ID = "fcm_order_id";
    public static final String EXTRA_FROM_FCM = "from_fcm";

    private NotificationHelper() {
    }

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ORDERS,
                "Orders",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("New order alerts");
        channel.enableVibration(true);

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    public static void showNewOrderNotification(
            Context context,
            String orderId,
            String title,
            String body
    ) {
        ensureChannels(context);

        Intent intent;
        if (orderId != null && !orderId.trim().isEmpty()) {
            intent = new Intent(context, OrderDetailActivity.class);
            intent.putExtra(OrderDetailActivity.EXTRA_ORDER_ID, orderId.trim());
            intent.putExtra(EXTRA_FROM_FCM, true);
        } else {
            intent = new Intent(context, OrdersActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // If user is logged out, start login first (OrderDetail will finish if not authed)
        Intent loginGate = new Intent(context, LoginActivity.class);
        loginGate.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Prefer order detail; LoginActivity already redirects if session exists

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                orderId != null ? orderId.hashCode() : 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String safeTitle = (title != null && !title.isEmpty()) ? title : "New order";
        String safeBody = (body != null && !body.isEmpty()) ? body : "Tap to view order";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ORDERS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(context)
                    .notify(orderId != null ? orderId.hashCode() : (int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS not granted on API 33+
            e.printStackTrace();
        }
    }
}