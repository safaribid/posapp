package com.safaribid.pos.models;

/**
 * Flexible lat/lng from jsonb or nested objects.
 */
public class GeoPoint {
    private double lat;
    private double lng;
    private Double latitude;
    private Double longitude;

    public double resolvedLat() {
        if (latitude != null) return latitude;
        return lat;
    }

    public double resolvedLng() {
        if (longitude != null) return longitude;
        return lng;
    }

    public boolean isValid() {
        double a = resolvedLat();
        double b = resolvedLng();
        return !(a == 0.0 && b == 0.0) && !Double.isNaN(a) && !Double.isNaN(b);
    }
}