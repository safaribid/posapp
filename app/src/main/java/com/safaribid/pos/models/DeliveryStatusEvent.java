package com.safaribid.pos.models;

import com.google.gson.annotations.SerializedName;

public class DeliveryStatusEvent {

    @SerializedName("deliveryId")
    private String deliveryId;

    /** Preferred if backend adds it later */
    @SerializedName(value = "shopOrderId", alternate = {"shop_order_id"})
    private String shopOrderId;

    /** Current backend shape */
    @SerializedName(value = "shopOrder", alternate = {"shop_order"})
    private ShopOrderRef shopOrder;

    private int status;

    @SerializedName(value = "shopOrderStatus", alternate = {"shop_order_status"})
    private Integer shopOrderStatus;

    private String action;
    private String timestamp;

    public String getDeliveryId() { return deliveryId; }

    public int getStatus() { return status; }

    public Integer getShopOrderStatus() {
        if (shopOrderStatus != null) return shopOrderStatus;
        if (shopOrder != null) return shopOrder.status;
        return null;
    }

    public String getAction() { return action; }

    public String getTimestamp() { return timestamp; }

    /** Always use this — works for flat or nested payload */
    public String resolveShopOrderId() {
        if (shopOrderId != null && !shopOrderId.isEmpty()) return shopOrderId;
        if (shopOrder != null && shopOrder.id != null && !shopOrder.id.isEmpty()) {
            return shopOrder.id;
        }
        return null;
    }

    /** Human label for delivery progress (driver side). */
    public String getStatusLabel() {
        switch (status) {
            case 2: return "Searching for driver";
            case 3: return "Driver accepted";
            case 4: return "Driver heading for pickup";
            case 5: return "Driver is here";
            case 6: return "Left the shop";
            case 7: return "At customer";
            case 8: return "Delivered";
            default: return "Delivery status " + status;
        }
    }

    public static class ShopOrderRef {
        public String id;
        public Integer status;
    }
}
