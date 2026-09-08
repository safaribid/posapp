package com.safaribid.pos.models;

public class BusinessInfo {
    private String id;
    private String uid;
    private String name;
    private String phone;
    private GeoPoint coords;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public GeoPoint getCoords() { return coords; }
    public void setCoords(GeoPoint coords) { this.coords = coords; }
}