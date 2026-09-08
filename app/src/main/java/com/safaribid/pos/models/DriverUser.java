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

        // 1) Direct keys on profile (rare)
        String direct = firstString(profile,
                "plate", "plate_number", "number_plate", "vehicle_plate",
                "reg_number", "registration");
        if (direct != null) return direct;

        // 2) vehicle_details.plate  ← actual backend shape
        if (profile.has("vehicle_details") && profile.get("vehicle_details").isJsonObject()) {
            JsonObject details = profile.getAsJsonObject("vehicle_details");
            String plate = firstString(details,
                    "plate", "plate_number", "number_plate", "reg_number");
            if (plate != null) return plate;
        }

        // 3) vehicle_info (older / alternate)
        if (profile.has("vehicle_info") && profile.get("vehicle_info").isJsonObject()) {
            JsonObject info = profile.getAsJsonObject("vehicle_info");
            String plate = firstString(info,
                    "plate", "plate_number", "number_plate", "reg_number");
            if (plate != null) return plate;
        }

        // 4) nested vehicle { plate }
        if (profile.has("vehicle") && profile.get("vehicle").isJsonObject()) {
            JsonObject v = profile.getAsJsonObject("vehicle");
            String plate = firstString(v, "plate", "plate_number", "number_plate");
            if (plate != null) return plate;
        }

        return "—";
    }

    private static String firstString(JsonObject o, String... keys) {
        if (o == null) return null;
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                try {
                    String v = o.get(k).getAsString();
                    if (v != null && !v.trim().isEmpty()) return v.trim();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}