package com.safaribid.pos.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Order {

    private String id;
    private String uid;
    private String bid;

    @SerializedName("master_order_id")
    private String masterOrderId;

    private int status;

    @SerializedName("total_price")
    private double totalPrice;

    @SerializedName("shipping_address")
    private ShippingAddress shippingAddress;

    @SerializedName("payment_method")
    private String paymentMethod;

    @SerializedName("payment_status")
    private String paymentStatus;

    @SerializedName("payment_reference")
    private String paymentReference;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("updated_at")
    private String updatedAt;

    private Customer customer;
    private List<OrderItem> items;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getBid() { return bid; }
    public void setBid(String bid) { this.bid = bid; }

    public String getMasterOrderId() { return masterOrderId; }
    public void setMasterOrderId(String masterOrderId) { this.masterOrderId = masterOrderId; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    // Convenience helpers for the list UI
    public String getCustomerDisplayName() {
        if (customer != null) {
            String name = (customer.getFname() != null ? customer.getFname() : "") + " " +
                    (customer.getLname() != null ? customer.getLname() : "");
            return name.trim().isEmpty() ? "Customer" : name.trim();
        }
        if (shippingAddress != null && shippingAddress.getName() != null) {
            return shippingAddress.getName();
        }
        return "Customer";
    }

    public String getStatusLabel() {
        switch (status) {
            case 1: return "Pending";
            case 2: return "New Order";
            case 3: return "Confirmed";
            case 4: return "Preparing";
            case 5: return "Ready for Pickup";
            case 6: return "Driver on the way";
            case 7: return "Driver is here";
            case 9:
            case 10: return "Rejected";
            default: return "Status " + status;
        }
    }
}