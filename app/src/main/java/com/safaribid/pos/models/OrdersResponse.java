package com.safaribid.pos.models;

import java.util.List;

public class OrdersResponse {
    private boolean success;
    private List<Order> data;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<Order> getData() {
        return data;
    }

    public void setData(List<Order> data) {
        this.data = data;
    }
}