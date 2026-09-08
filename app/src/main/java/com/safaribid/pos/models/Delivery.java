package com.safaribid.pos.models;

import com.google.gson.annotations.SerializedName;

public class Delivery {

    private String id;

    private String customer;
    private String bid;
    private String driver;

    @SerializedName("shop_order_id")
    private String shopOrderId;

    @SerializedName("tracking_code")
    private String trackingCode;

    private int status;

    @SerializedName("pickup_address")
    private AddressInfo pickupAddress;

    @SerializedName("dropoff_address")
    private AddressInfo dropoffAddress;

    @SerializedName("pickup_coords")
    private GeoPoint pickupCoords;

    @SerializedName("dropoff_coords")
    private GeoPoint dropoffCoords;

    private BusinessInfo business;

    @SerializedName("driver_user")
    private DriverUser driverUser;

    @SerializedName("driver_location")
    private DriverLocation driverLocation;

    @SerializedName("shop_order")
    private Order shopOrder;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }

    public String getBid() { return bid; }
    public void setBid(String bid) { this.bid = bid; }

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }

    public String getShopOrderId() { return shopOrderId; }
    public void setShopOrderId(String shopOrderId) { this.shopOrderId = shopOrderId; }

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public AddressInfo getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(AddressInfo pickupAddress) { this.pickupAddress = pickupAddress; }

    public AddressInfo getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(AddressInfo dropoffAddress) { this.dropoffAddress = dropoffAddress; }

    public GeoPoint getPickupCoords() { return pickupCoords; }
    public void setPickupCoords(GeoPoint pickupCoords) { this.pickupCoords = pickupCoords; }

    public GeoPoint getDropoffCoords() { return dropoffCoords; }
    public void setDropoffCoords(GeoPoint dropoffCoords) { this.dropoffCoords = dropoffCoords; }

    public BusinessInfo getBusiness() { return business; }
    public void setBusiness(BusinessInfo business) { this.business = business; }

    public DriverUser getDriverUser() { return driverUser; }
    public void setDriverUser(DriverUser driverUser) { this.driverUser = driverUser; }

    public DriverLocation getDriverLocation() { return driverLocation; }
    public void setDriverLocation(DriverLocation driverLocation) { this.driverLocation = driverLocation; }

    public Order getShopOrder() { return shopOrder; }
    public void setShopOrder(Order shopOrder) { this.shopOrder = shopOrder; }

    public String driverStatusLabel() {
        switch (status) {
            case 2: return "Searching for driver";
            case 3: return "Driver accepted";
            case 4: return "Driver on the way to shop";
            case 5: return "Driver is here";
            case 6: return "Left the shop";
            case 7: return "At customer";
            case 8: return "Delivered";
            default: return "Delivery status " + status;
        }
    }

    public String pickupTitle() {
        if (business != null && business.getName() != null && !business.getName().isEmpty()) {
            return business.getName();
        }
        if (pickupAddress != null && pickupAddress.getName() != null) {
            return pickupAddress.getName();
        }
        return "Pickup";
    }

    public String pickupSubtitle() {
        return pickupAddress != null ? pickupAddress.displayLine() : "—";
    }

    public String dropoffSubtitle() {
        return dropoffAddress != null ? dropoffAddress.displayLine() : "—";
    }
}