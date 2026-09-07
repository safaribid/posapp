package com.safaribid.pos.models;

public class TrackDeliveryResponse {
    private boolean success;
    private Delivery data;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Delivery getData() { return data; }
    public void setData(Delivery data) { this.data = data; }
}