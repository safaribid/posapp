package com.safaribid.pos.models;

/**
 * Backend AddressProps: { name, lat, lng }
 * - pickup.name  = business name
 * - dropoff.name = street / full address text
 */
public class AddressInfo {

    private String name;
    private double lat;
    private double lng;

    // optional extras if some payloads add them
    private String address;
    private String city;
    private String details;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public boolean hasCoords() {
        return !(lat == 0.0 && lng == 0.0)
                && !Double.isNaN(lat)
                && !Double.isNaN(lng);
    }

    /** Line under PICKUP title or the single DROPOFF line */
    public String displayLine() {
        if (address != null && !address.trim().isEmpty()) {
            StringBuilder sb = new StringBuilder(address.trim());
            if (city != null && !city.trim().isEmpty()) {
                sb.append(", ").append(city.trim());
            }
            return sb.toString();
        }
        if (details != null && !details.trim().isEmpty()) {
            return details.trim();
        }
        // dropoff: name is the address
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        return "—";
    }

    public String titleOrName() {
        return name != null && !name.trim().isEmpty() ? name.trim() : "—";
    }
}
