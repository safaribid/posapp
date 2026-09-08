package com.safaribid.pos.models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * shop_order_id / delivery_id → latest delivery status for list + detail UI.
 */
public final class DeliveryProgressStore {

    private static final DeliveryProgressStore INSTANCE = new DeliveryProgressStore();

    private final Map<String, Integer> statusByShopOrderId = new ConcurrentHashMap<>();
    private final Map<String, Integer> statusByDeliveryId = new ConcurrentHashMap<>();
    private final Map<String, String> deliveryIdByShopOrderId = new ConcurrentHashMap<>();

    private DeliveryProgressStore() {
    }

    public static DeliveryProgressStore get() {
        return INSTANCE;
    }

    public void put(String shopOrderId, String deliveryId, int deliveryStatus) {
        if (deliveryId != null && !deliveryId.isEmpty()) {
            statusByDeliveryId.put(deliveryId, deliveryStatus);
        }
        if (shopOrderId != null && !shopOrderId.isEmpty()) {
            statusByShopOrderId.put(shopOrderId, deliveryStatus);
            if (deliveryId != null && !deliveryId.isEmpty()) {
                deliveryIdByShopOrderId.put(shopOrderId, deliveryId);
            }
        }
    }

    public Integer getByShopOrderId(String shopOrderId) {
        if (shopOrderId == null) return null;
        return statusByShopOrderId.get(shopOrderId);
    }

    public Integer getByDeliveryId(String deliveryId) {
        if (deliveryId == null) return null;
        return statusByDeliveryId.get(deliveryId);
    }

    public static String labelFor(int deliveryStatus) {
        switch (deliveryStatus) {
            case 2: return "Searching for driver";
            case 3: return "Driver accepted";
            case 4: return "Driver heading for pickup";
            case 5: return "Driver is here";
            case 6: return "Left the shop";
            case 7: return "At customer";
            case 8: return "Delivered";
            default: return "Delivery status " + deliveryStatus;
        }
    }

    /** Chip text: delivery progress if active, else shop order label. */
    public static String displayLabel(Order order) {
        if (order == null) return "—";
        Integer ds = get().getByShopOrderId(order.getId());
        if (ds != null && ds >= 3) {
            return labelFor(ds);
        }
        return order.getStatusLabel();
    }
}
