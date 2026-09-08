package com.safaribid.pos.models;

public class AddressInfo {
    private String name;
    private String address;
    private String city;
    private String details;
    private String phone;
    private String label;
    private String formatted;
    private String street;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getFormatted() { return formatted; }
    public void setFormatted(String formatted) { this.formatted = formatted; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String displayLine() {
        if (formatted != null && !formatted.trim().isEmpty()) return formatted.trim();
        if (label != null && !label.trim().isEmpty()) return label.trim();
        if (street != null && !street.trim().isEmpty()) {
            StringBuilder sb = new StringBuilder(street.trim());
            if (city != null && !city.trim().isEmpty()) sb.append(", ").append(city.trim());
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        if (address != null && !address.trim().isEmpty()) sb.append(address.trim());
        if (city != null && !city.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city.trim());
        }
        if (sb.length() == 0 && details != null && !details.trim().isEmpty()) {
            sb.append(details.trim());
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }
}