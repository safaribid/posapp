package com.safaribid.pos.models;

import com.google.gson.annotations.SerializedName;

public class DriverLocation {

    private double lat;
    private double lng;
    private double heading;
    private double accuracy;
    private double speed;

    @SerializedName("updatedAt")
    private Long updatedAt;

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public double getHeading() { return heading; }
    public void setHeading(double heading) { this.heading = heading; }

    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }

    public boolean isValid() {
        return !(lat == 0.0 && lng == 0.0)
                && !Double.isNaN(lat)
                && !Double.isNaN(lng);
    }
}