package com.safaribid.pos.models;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class DriverUser {

    private String id;
    private String fname;
    private String lname;
    private String email;
    private String phone;
    private String photo;

    /** onboarding profile — plate / vehicle may live here */
    private JsonObject profile;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFname() { return fname; }
    public void setFname(String fname) { this.fname = fname; }

    public String getLname() { return lname; }
    public void setLname(String lname) { this.lname = lname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public JsonObject getProfile() { return profile; }
    public void setProfile(JsonObject profile) { this.profile = profile; }

    public String displayName() {
        String n = ((fname != null ? fname : "") + " " + (lname != null ? lname : "")).trim();
        return n.isEmpty() ? "Driver" : n;
    }

    public String plateNumber() {
        if (profile == null) return "—";
        String[] keys = {
                "plate", "plate_number", "number_plate", "vehicle_plate",
                "reg_number", "registration", "vehicle_reg"
        };
        for (String k : keys) {
            if (profile.has(k) && !profile.get(k).isJsonNull()) {
                String v = profile.get(k).getAsString();
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        }
        // nested vehicle object
        if (profile.has("vehicle") && profile.get("vehicle").isJsonObject()) {
            JsonObject v = profile.getAsJsonObject("vehicle");
            for (String k : keys) {
                if (v.has(k) && !v.get(k).isJsonNull()) {
                    String s = v.get(k).getAsString();
                    if (s != null && !s.trim().isEmpty()) return s.trim();
                }
            }
        }
        return "—";
    }
}