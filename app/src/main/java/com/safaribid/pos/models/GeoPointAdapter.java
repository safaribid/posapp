package com.safaribid.pos.models;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Type;

public class GeoPointAdapter implements JsonDeserializer<GeoPoint> {

    @Override
    public GeoPoint deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        // "POINT(36.8 -1.29)"
        if (json.isJsonPrimitive()) {
            JsonPrimitive p = json.getAsJsonPrimitive();
            if (p.isString()) {
                return GeoPoint.fromWkt(p.getAsString());
            }
            return null;
        }

        // { "lat": ..., "lng": ... } or latitude/longitude
        if (json.isJsonObject()) {
            JsonObject o = json.getAsJsonObject();
            Double lat = firstDouble(o, "lat", "latitude", "y");
            Double lng = firstDouble(o, "lng", "lon", "longitude", "x");
            if (lat == null || lng == null) return null;
            return new GeoPoint(lat, lng);
        }

        // [lng, lat] GeoJSON-style
        if (json.isJsonArray() && json.getAsJsonArray().size() >= 2) {
            try {
                double a = json.getAsJsonArray().get(0).getAsDouble();
                double b = json.getAsJsonArray().get(1).getAsDouble();
                // assume [lng, lat]
                return new GeoPoint(b, a);
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    private static Double firstDouble(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                try {
                    return o.get(k).getAsDouble();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
