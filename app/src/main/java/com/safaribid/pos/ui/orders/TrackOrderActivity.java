package com.safaribid.pos.ui.orders;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.safaribid.pos.R;
import com.safaribid.pos.auth.AuthManager;
import com.safaribid.pos.models.AddressInfo;
import com.safaribid.pos.models.Delivery;
import com.safaribid.pos.models.DeliveryListResponse;
import com.safaribid.pos.models.DeliveryStatusEvent;
import com.safaribid.pos.models.DriverLocation;
import com.safaribid.pos.models.DriverUser;
import com.safaribid.pos.models.GeoPoint;
import com.safaribid.pos.models.TrackDeliveryResponse;
import com.safaribid.pos.network.ApiClient;
import com.safaribid.pos.network.ApiService;
import com.safaribid.pos.network.SocketManager;
import com.safaribid.pos.utils.AppConfig;
import com.safaribid.pos.utils.RouteHelper;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TrackOrderActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_TRACKING_CODE = "tracking_code";
    public static final String EXTRA_DELIVERY_ID = "delivery_id";
    public static final String EXTRA_SHOP_ORDER_ID = "shop_order_id";

    private static final String TAG = "TrackOrder";

    private TextView txtTrackingCode, txtPickupName, txtPickupAddress, txtDropoffAddress;
    private TextView txtDriverStatus, txtDriverName, txtDriverPhone, txtPlate;
    private ImageView imgDriver;
    private View viewOnlineDot;
    private ProgressBar progressBar;
    private ImageButton btnClose;

    private GoogleMap googleMap;
    private Marker markerA, markerB, markerDriver;
    private Polyline routeLine;

    private AuthManager authManager;
    private final Gson gson = new Gson();

    private String trackingCode;
    private String deliveryId;
    private String shopOrderId;
    private Delivery delivery;

    private final SocketManager.OrderListener socketListener = new SocketManager.OrderListener() {
        @Override
        public void onOrderRequest(String orderJson) { }

        @Override
        public void onDeliveryStatus(String payloadJson) {
            runOnUiThread(() -> handleDeliveryStatus(payloadJson));
        }

        @Override
        public void onDriverLocation(String payloadJson) {
            runOnUiThread(() -> handleDriverLocation(payloadJson));
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_order);

        authManager = new AuthManager(this);

        trackingCode = getIntent().getStringExtra(EXTRA_TRACKING_CODE);
        deliveryId = getIntent().getStringExtra(EXTRA_DELIVERY_ID);
        shopOrderId = getIntent().getStringExtra(EXTRA_SHOP_ORDER_ID);

        bindViews();
        btnClose.setOnClickListener(v -> finish());

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        resolveAndLoad();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SocketManager.getInstance().setOrderListener(socketListener);
        String uid = authManager.getUserId();
        if (uid != null) {
            SocketManager.getInstance().connect(uid);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        SocketManager.getInstance().setOrderListener(null);
    }

    private void bindViews() {
        txtTrackingCode = findViewById(R.id.txtTrackingCode);
        txtPickupName = findViewById(R.id.txtPickupName);
        txtPickupAddress = findViewById(R.id.txtPickupAddress);
        txtDropoffAddress = findViewById(R.id.txtDropoffAddress);
        txtDriverStatus = findViewById(R.id.txtDriverStatus);
        txtDriverName = findViewById(R.id.txtDriverName);
        txtDriverPhone = findViewById(R.id.txtDriverPhone);
        txtPlate = findViewById(R.id.txtPlate);
        imgDriver = findViewById(R.id.imgDriver);
        viewOnlineDot = findViewById(R.id.viewOnlineDot);
        progressBar = findViewById(R.id.progressBar);
        btnClose = findViewById(R.id.btnClose);
    }

    private void resolveAndLoad() {
        if (trackingCode != null && !trackingCode.trim().isEmpty()) {
            loadByTrackingCode(trackingCode.trim());
            return;
        }
        if (deliveryId != null && !deliveryId.trim().isEmpty()) {
            loadByDeliveryId(deliveryId.trim());
            return;
        }
        if (shopOrderId != null && !shopOrderId.trim().isEmpty()) {
            findDeliveryForShopOrder(shopOrderId.trim());
            return;
        }
        Toast.makeText(this, "Missing tracking info", Toast.LENGTH_LONG).show();
        finish();
    }

    private void loadByTrackingCode(String code) {
        showLoading(true);
        String token = authManager.getBearerToken();
        if (token == null) {
            showLoading(false);
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = AppConfig.serverOrigin()
                + "/api/account/customer/deliveries/track?code="
                + Uri.encode(code);

        ApiClient.getApiService().trackDelivery(token, url)
                .enqueue(new Callback<TrackDeliveryResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TrackDeliveryResponse> call,
                                           @NonNull Response<TrackDeliveryResponse> response) {
                        showLoading(false);
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getData() != null) {
                            applyDelivery(response.body().getData());
                        } else {
                            Toast.makeText(TrackOrderActivity.this,
                                    "Track failed (" + response.code() + ")",
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TrackDeliveryResponse> call,
                                          @NonNull Throwable t) {
                        showLoading(false);
                        Toast.makeText(TrackOrderActivity.this,
                                "Network: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadByDeliveryId(String id) {
        showLoading(true);
        String token = authManager.getBearerToken();
        String uid = authManager.getUserId();
        if (token == null || uid == null) {
            showLoading(false);
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = AppConfig.serverOrigin()
                + "/api/business/deliveries/view?uid="
                + Uri.encode(uid)
                + "&id="
                + Uri.encode(id);

        ApiClient.getApiService().getBusinessDelivery(token, url)
                .enqueue(new Callback<TrackDeliveryResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TrackDeliveryResponse> call,
                                           @NonNull Response<TrackDeliveryResponse> response) {
                        showLoading(false);
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getData() != null) {
                            Delivery d = response.body().getData();
                            // Prefer track endpoint for live driver_location when code exists
                            if (d.getTrackingCode() != null && !d.getTrackingCode().isEmpty()) {
                                loadByTrackingCode(d.getTrackingCode());
                            } else {
                                applyDelivery(d);
                            }
                        } else {
                            Toast.makeText(TrackOrderActivity.this,
                                    "Delivery not found (" + response.code() + ")",
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TrackDeliveryResponse> call,
                                          @NonNull Throwable t) {
                        showLoading(false);
                        Toast.makeText(TrackOrderActivity.this,
                                "Network: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void findDeliveryForShopOrder(String orderId) {
        showLoading(true);
        String token = authManager.getBearerToken();
        String uid = authManager.getUserId();
        if (token == null || uid == null) {
            showLoading(false);
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = AppConfig.serverOrigin()
                + "/api/business/deliveries?uid="
                + Uri.encode(uid)
                + "&limit=50";

        ApiClient.getApiService().getBusinessDeliveries(token, url)
                .enqueue(new Callback<DeliveryListResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<DeliveryListResponse> call,
                                           @NonNull Response<DeliveryListResponse> response) {
                        showLoading(false);
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getData() != null) {
                            for (Delivery d : response.body().getData()) {
                                if (orderId.equals(d.getShopOrderId())) {
                                    if (d.getTrackingCode() != null && !d.getTrackingCode().isEmpty()) {
                                        loadByTrackingCode(d.getTrackingCode());
                                    } else {
                                        applyDelivery(d);
                                    }
                                    return;
                                }
                            }
                            Toast.makeText(TrackOrderActivity.this,
                                    "No delivery for this order yet", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(TrackOrderActivity.this,
                                    "Could not load deliveries", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<DeliveryListResponse> call,
                                          @NonNull Throwable t) {
                        showLoading(false);
                        Toast.makeText(TrackOrderActivity.this,
                                "Network: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void applyDelivery(Delivery d) {
        this.delivery = d;
        if (d.getId() != null) deliveryId = d.getId();
        if (d.getTrackingCode() != null) trackingCode = d.getTrackingCode();

        txtTrackingCode.setText(trackingCode != null ? trackingCode : "—");

        // PICKUP: name = shop; subtitle only if we have a real street field
        AddressInfo pickup = d.getPickupAddress();
        if (pickup != null) {
            txtPickupName.setText(pickup.titleOrName());
            // Prefer business street if API ever adds it; else show name only once
            String sub = pickup.getAddress() != null ? pickup.displayLine() : "—";
            // If displayLine() would repeat the shop name, keep subtitle as em dash
            if (sub.equals(pickup.titleOrName())) {
                txtPickupAddress.setText("—");
            } else {
                txtPickupAddress.setText(sub);
            }
        } else if (d.getBusiness() != null && d.getBusiness().getName() != null) {
            txtPickupName.setText(d.getBusiness().getName());
            txtPickupAddress.setText("—");
        } else {
            txtPickupName.setText("Pickup");
            txtPickupAddress.setText("—");
        }

        // DROPOFF: name is the address text
        AddressInfo drop = d.getDropoffAddress();
        if (drop != null) {
            txtDropoffAddress.setText(drop.displayLine()); // uses name
        } else {
            txtDropoffAddress.setText("—");
        }

        txtDriverStatus.setText(d.driverStatusLabel());

        Log.d(TAG, "pickupCoords=" + d.getPickupCoords()
                + " dropoffCoords=" + d.getDropoffCoords());

        DriverUser du = d.getDriverUser();
        if (du != null) {
            txtDriverName.setText(du.displayName());
            txtDriverPhone.setText(du.getPhone() != null ? du.getPhone() : "—");
            txtPlate.setText(du.plateNumber());

            String photo = du.getPhoto();
            if (photo != null && !photo.trim().isEmpty()) {
                Glide.with(this)
                        .load(photo.trim())
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_round)
                        .error(R.mipmap.ic_launcher_round)
                        .into(imgDriver);
            } else {
                imgDriver.setImageResource(R.mipmap.ic_launcher_round);
            }
        } else {
            txtDriverName.setText("Waiting for driver");
            txtDriverPhone.setText("—");
            txtPlate.setText("—");
            imgDriver.setImageResource(R.mipmap.ic_launcher_round);
        }

        updateMapFromDelivery();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        updateMapFromDelivery();
    }

    private void updateMapFromDelivery() {
        if (googleMap == null || delivery == null) return;

        LatLng pickup = null;
        LatLng dropoff = null;

        AddressInfo pa = delivery.getPickupAddress();
        if (pa != null && pa.hasCoords()) {
            pickup = new LatLng(pa.getLat(), pa.getLng());
        } else {
            pickup = coordsOf(delivery.getPickupCoords()); // WKT fallback
        }

        AddressInfo da = delivery.getDropoffAddress();
        if (da != null && da.hasCoords()) {
            dropoff = new LatLng(da.getLat(), da.getLng());
        } else {
            dropoff = coordsOf(delivery.getDropoffCoords());
        }

        if (markerA != null) markerA.remove();
        if (markerB != null) markerB.remove();
        if (routeLine != null) routeLine.remove();

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        boolean hasPoint = false;

        if (pickup != null) {
            markerA = googleMap.addMarker(new MarkerOptions()
                    .position(pickup)
                    .title("A · Pickup")
                    .snippet(pa != null ? pa.titleOrName() : "Pickup")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            bounds.include(pickup);
            hasPoint = true;
        }

        if (dropoff != null) {
            markerB = googleMap.addMarker(new MarkerOptions()
                    .position(dropoff)
                    .title("B · Dropoff")
                    .snippet(da != null ? da.displayLine() : "Dropoff")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            bounds.include(dropoff);
            hasPoint = true;
        }

        if (pickup != null && dropoff != null) {
            // Remove old route
            if (routeLine != null) {
                routeLine.remove();
                routeLine = null;
            }

            final LatLng origin = pickup;
            final LatLng dest = dropoff;

            RouteHelper.fetchDrivingRoute(origin, dest, new RouteHelper.Callback() {
                @Override
                public void onRoute(List<LatLng> points) {
                    if (googleMap == null || isFinishing()) return;
                    if (routeLine != null) routeLine.remove();
                    routeLine = googleMap.addPolyline(new PolylineOptions()
                            .addAll(points)
                            .width(10f)
                            .color(0xFF4CAF50)); // same green as your mockups
                }

                @Override
                public void onError(String message) {
                    Log.w("TrackOrder", "Route failed, using straight line: " + message);
                    // Fallback so the map is never empty
                    if (googleMap == null || isFinishing()) return;
                    if (routeLine != null) routeLine.remove();
                    routeLine = googleMap.addPolyline(new PolylineOptions()
                            .add(origin, dest)
                            .width(8f)
                            .color(0xFF4CAF50));
                }
            });
        }

        DriverLocation loc = delivery.getDriverLocation();
        if (loc != null && loc.isValid()) {
            updateDriverMarker(new LatLng(loc.getLat(), loc.getLng()));
            bounds.include(new LatLng(loc.getLat(), loc.getLng()));
            hasPoint = true;
        }

        if (hasPoint) {
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120));
            } catch (Exception e) {
                LatLng focus = pickup != null ? pickup : dropoff;
                if (focus != null) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, 14f));
                }
            }
        }
    }

    private void updateDriverMarker(LatLng pos) {
        if (googleMap == null || pos == null) return;
        if (markerDriver == null) {
            markerDriver = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Driver")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        } else {
            markerDriver.setPosition(pos);
        }
        viewOnlineDot.setVisibility(View.VISIBLE);
    }

    private LatLng coordsOf(GeoPoint p) {
        if (p == null || !p.isValid()) return null;
        return new LatLng(p.resolvedLat(), p.resolvedLng());
    }

    private void handleDeliveryStatus(String json) {
        try {
            DeliveryStatusEvent event = gson.fromJson(json, DeliveryStatusEvent.class);
            if (event == null) return;
            if (deliveryId != null && event.getDeliveryId() != null
                    && !deliveryId.equals(event.getDeliveryId())) {
                return;
            }
            if (delivery != null) {
                delivery.setStatus(event.getStatus());
                txtDriverStatus.setText(delivery.driverStatusLabel());
            }
            // Refresh snapshot for richer fields after status changes
            if (trackingCode != null) {
                loadByTrackingCode(trackingCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "delivery_status parse", e);
        }
    }

    private void handleDriverLocation(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return;

            if (root.has("deliveryId") && deliveryId != null) {
                String did = root.get("deliveryId").getAsString();
                if (!deliveryId.equals(did)) return;
            }

            JsonObject loc = root.has("location") && root.get("location").isJsonObject()
                    ? root.getAsJsonObject("location")
                    : root;

            if (!loc.has("lat") || !loc.has("lng")) return;
            double lat = loc.get("lat").getAsDouble();
            double lng = loc.get("lng").getAsDouble();
            if (lat == 0 && lng == 0) return;

            updateDriverMarker(new LatLng(lat, lng));
        } catch (Exception e) {
            Log.e(TAG, "driver_location parse", e);
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
