package com.safaribid.pos.models;

public class OrderUpdateResponse {
    private boolean success;
    private Order data;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Order getData() {
        return data;
    }

    public void setData(Order data) {
        this.data = data;
    }
}
