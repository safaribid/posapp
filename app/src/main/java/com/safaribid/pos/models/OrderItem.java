package com.safaribid.pos.models;

import com.google.gson.annotations.SerializedName;

public class OrderItem {

    private String id;
    private double price;
    private int quantity;

    @SerializedName("order_id")
    private String orderId;

    @SerializedName("product_id")
    private String productId;

    private Product product;

    @SerializedName("created_at")
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getProductTitle() {
        return product != null && product.getTitle() != null ? product.getTitle() : "Item";
    }
}