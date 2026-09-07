package com.safaribid.pos.models;

public class AddressInfo {
    private String name;
    private String address;
    private String city;
    private String details;
    private String phone;

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

    public String displayLine() {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.trim().isEmpty()) sb.append(address.trim());
        if (city != null && !city.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city.trim());
        }
        if (details != null && !details.trim().isEmpty() && sb.length() == 0) {
            sb.append(details.trim());
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }
}