package com.safaribid.pos.models;

import com.google.gson.annotations.SerializedName;

/**
 * Socket event: "delivery_status"
 * Base fields from backend notifyDeliveryStatus + optional extras + timestamp.
 */
public class DeliveryStatusEvent {

    @SerializedName("deliveryId")
    private String deliveryId;

    private int status;

    @SerializedName("shopOrderStatus")
    private Integer shopOrderStatus;

    @SerializedName("shop_order_id")
    private String shopOrderId;

    private String timestamp;

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Integer getShopOrderStatus() {
        return shopOrderStatus;
    }

    public void setShopOrderStatus(Integer shopOrderStatus) {
        this.shopOrderStatus = shopOrderStatus;
    }

    public String getShopOrderId() {
        return shopOrderId;
    }

    public void setShopOrderId(String shopOrderId) {
        this.shopOrderId = shopOrderId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    /** Human label for delivery progress (driver side). */
    public String getStatusLabel() {
        switch (status) {
            case 2:
                return "Searching for driver";
            case 3:
                return "Driver accepted";
            case 4:
                return "Driver on the way to shop";
            case 5:
                return "Driver at pickup";
            case 6:
                return "On the way to customer";
            case 7:
                return "Driver at customer";
            case 8:
                return "Delivered";
            default:
                return "Delivery status " + status;
        }
    }
}