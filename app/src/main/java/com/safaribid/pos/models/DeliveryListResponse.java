package com.safaribid.pos.models;

import java.util.List;

public class DeliveryListResponse {
    private boolean success;
    private List<Delivery> data;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public List<Delivery> getData() { return data; }
    public void setData(List<Delivery> data) { this.data = data; }
}