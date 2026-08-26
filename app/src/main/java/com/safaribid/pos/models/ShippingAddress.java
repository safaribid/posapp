package com.safaribid.pos.models;

public class ShippingAddress {
    private String city;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String details;

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}