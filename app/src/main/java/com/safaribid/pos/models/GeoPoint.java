package com.safaribid.pos.models;

/**
 * Coordinates may arrive as:
 * - object: { "lat": 1.2, "lng": 36.8 }
 * - string: "POINT(lng lat)" (PostGIS / geography)
 * - string GeoJSON, etc.
 */
public class GeoPoint {

    private double lat;
    private double lng;

    public GeoPoint() {
    }

    public GeoPoint(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public double resolvedLat() {
        return lat;
    }

    public double resolvedLng() {
        return lng;
    }

    public boolean isValid() {
        return !(lat == 0.0 && lng == 0.0)
                && !Double.isNaN(lat)
                && !Double.isNaN(lng);
    }

    /**
     * Parse WKT POINT. PostGIS order is typically: POINT(longitude latitude)
     */
    public static GeoPoint fromWkt(String wkt) {
        if (wkt == null) return null;
        String s = wkt.trim();
        if (s.isEmpty()) return null;

        // POINT(36.8 -1.29) or SRID=4326;POINT(...)
        int start = s.indexOf('(');
        int end = s.indexOf(')');
        if (start < 0 || end < 0 || end <= start + 1) return null;

        String inside = s.substring(start + 1, end).trim();
        String[] parts = inside.split("\\s+");
        if (parts.length < 2) return null;

        try {
            double first = Double.parseDouble(parts[0]);
            double second = Double.parseDouble(parts[1]);
            // WKT: lng lat
            return new GeoPoint(second, first);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
