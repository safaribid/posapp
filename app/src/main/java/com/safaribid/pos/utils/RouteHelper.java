package com.safaribid.pos.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.safaribid.pos.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches a driving route between two points via Google Directions API.
 * Uses MAPS_API_KEY from BuildConfig (local.properties) — do not hardcode.
 */
public final class RouteHelper {

    private static final String TAG = "RouteHelper";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onRoute(List<LatLng> points);
        void onError(String message);
    }

    private RouteHelper() {
    }

    public static void fetchDrivingRoute(LatLng origin, LatLng destination, Callback callback) {
        if (origin == null || destination == null) {
            MAIN.post(() -> callback.onError("Missing origin or destination"));
            return;
        }

        String key = BuildConfig.MAPS_API_KEY;
        if (key == null || key.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("MAPS_API_KEY missing"));
            return;
        }

        String url = String.format(Locale.US,
                "https://maps.googleapis.com/maps/api/directions/json"
                        + "?origin=%f,%f"
                        + "&destination=%f,%f"
                        + "&mode=driving"
                        + "&key=%s",
                origin.latitude, origin.longitude,
                destination.latitude, destination.longitude,
                key.trim()
        );

        EXEC.execute(() -> {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = CLIENT.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    MAIN.post(() -> callback.onError("Directions HTTP " + response.code()));
                    return;
                }

                JSONObject json = new JSONObject(body);
                String status = json.optString("status", "");
                if (!"OK".equals(status)) {
                    String err = json.optString("error_message", status);
                    Log.e(TAG, "Directions status=" + status + " " + err);
                    MAIN.post(() -> callback.onError("Directions: " + status));
                    return;
                }

                JSONArray routes = json.optJSONArray("routes");
                if (routes == null || routes.length() == 0) {
                    MAIN.post(() -> callback.onError("No routes"));
                    return;
                }

                JSONObject overview = routes.getJSONObject(0).optJSONObject("overview_polyline");
                String encoded = overview != null ? overview.optString("points", "") : "";
                List<LatLng> points = decodePolyline(encoded);

                if (points.isEmpty()) {
                    MAIN.post(() -> callback.onError("Empty polyline"));
                    return;
                }

                MAIN.post(() -> callback.onRoute(points));
            } catch (IOException e) {
                Log.e(TAG, "Directions network error", e);
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Directions parse error", e);
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /** Google encoded polyline algorithm */
    public static List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return poly;

        int index = 0;
        int lat = 0;
        int lng = 0;
        int len = encoded.length();

        while (index < len) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dlng;

            poly.add(new LatLng(lat / 1e5, lng / 1e5));
        }
        return poly;
    }
}
