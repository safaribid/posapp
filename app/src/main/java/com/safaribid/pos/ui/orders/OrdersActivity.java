package com.safaribid.pos.ui.orders;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.safaribid.pos.R;
import com.safaribid.pos.auth.AuthManager;
import com.safaribid.pos.auth.LoginActivity;
import com.safaribid.pos.models.Delivery;
import com.safaribid.pos.models.DeliveryListResponse;
import com.safaribid.pos.models.DeliveryProgressStore;
import com.safaribid.pos.models.DeliveryStatusEvent;
import com.safaribid.pos.models.Order;
import com.safaribid.pos.models.OrderUpdateResponse;
import com.safaribid.pos.models.OrdersResponse;
import com.safaribid.pos.network.ApiClient;
import com.safaribid.pos.network.ApiService;
import com.safaribid.pos.network.SocketManager;
import com.safaribid.pos.notifications.NotificationHelper;
import com.safaribid.pos.printer.PrinterPickerActivity;
import com.safaribid.pos.utils.AppConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrdersActivity extends AppCompatActivity implements SocketManager.OrderListener {

    private static final String TAG = "OrdersActivity";

    private RecyclerView recyclerView;
    private ShimmerFrameLayout shimmerLayout;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearch;

    private Button btnFilterAll, btnFilterActive, btnFilterUnfulfilled, btnFilterPartial;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageButton btnMenu, btnNotifications;

    private OrderAdapter adapter;
    private AuthManager authManager;

    private final List<Order> allOrders = new ArrayList<>();
    private String currentQuery = "";
    private String currentFilter = "all"; // all | active | new | completed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders);

        authManager = new AuthManager(this);

        if (!authManager.isLoggedIn()) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        3001
                );
            }
        }
        NotificationHelper.ensureChannels(this);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        recyclerView = findViewById(R.id.recyclerOrders);
        shimmerLayout = findViewById(R.id.shimmerLayout);
        tvEmpty = findViewById(R.id.tvEmpty);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        etSearch = findViewById(R.id.etSearch);

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterActive = findViewById(R.id.btnFilterActive);
        btnFilterUnfulfilled = findViewById(R.id.btnFilterUnfulfilled);
        btnFilterPartial = findViewById(R.id.btnFilterPartial);

        btnMenu = findViewById(R.id.btnMenu);
        btnNotifications = findViewById(R.id.btnNotifications);

        // Header user name
        View header = navigationView.getHeaderView(0);
        TextView tvNavUser = header.findViewById(R.id.tvNavUser);
        String firstName = authManager.getFirstName();
        String email = authManager.getEmail();
        if (firstName != null && !firstName.isEmpty()) {
            tvNavUser.setText(firstName);
        } else if (email != null && !email.isEmpty()) {
            tvNavUser.setText(email);
        } else {
            tvNavUser.setText("User");
        }

        // Open drawer
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Notifications placeholder
        btnNotifications.setOnClickListener(v ->
                Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show());

        // Drawer item clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);

            if (id == R.id.nav_orders) {
                // Already on Orders
                return true;
            } else if (id == R.id.nav_printer) {
                startActivity(new Intent(this, PrinterPickerActivity.class));
                return true;
            } else if (id == R.id.nav_logout) {
                logout();
                return true;
            }
            return false;
        });

        // Mark Orders as selected
        navigationView.setCheckedItem(R.id.nav_orders);

        adapter = new OrderAdapter(new OrderAdapter.OnOrderActionListener() {
            @Override
            public void onOrderClick(Order order) {
                openOrderDetail(order);
            }

            @Override
            public void onAccept(Order order) {
                // Accept new order → status 3
                updateOrderStatus(order, 3);
            }

            @Override
            public void onReject(Order order) {
                updateOrderStatus(order, 11); // Reject → status 11
            }

            @Override
            public void onPrimaryAction(Order order) {
                openOrderDetail(order);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadOrders);

        setupSearch();
        setupFilters();
        updateFilterButtonStyles();

        // Socket
        SocketManager.getInstance().addOrderListener(this);
        String uid = authManager.getUserId();
        if (uid != null && !uid.isEmpty()) {
            SocketManager.getInstance().connect(uid);
        }

        SocketManager.getInstance().setConnectionListener(new SocketManager.ConnectionListener() {
            @Override public void onConnected() {
                Log.d("OrdersActivity", "socket connected");
            }
            @Override public void onDisconnected() {
                Log.d("OrdersActivity", "socket disconnected");
            }
            @Override public void onRegistered(String uid) {
                Log.d("OrdersActivity", "socket registered uid=" + uid);
                Toast.makeText(OrdersActivity.this, "Live updates on", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                Log.e("OrdersActivity", "socket error: " + message);
            }
        });

        // Handle back press for drawer
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        loadOrders();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SocketManager.getInstance().addOrderListener(this);
        // Refresh delivery labels when coming back from detail
        if (!allOrders.isEmpty()) {
            seedDeliveryProgress();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Keep listening in background if desired, or remove if not
        // SocketManager.getInstance().removeOrderListener(this);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s != null ? s.toString().trim().toLowerCase(Locale.getDefault()) : "";
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupFilters() {
        btnFilterAll.setOnClickListener(v -> setFilter("all"));
        btnFilterActive.setOnClickListener(v -> setFilter("active"));
        // rename unfulfilled → New Order
        btnFilterUnfulfilled.setText("New Order");
        btnFilterUnfulfilled.setOnClickListener(v -> setFilter("new"));
        // rename partial → Delivered
        btnFilterPartial.setText("Delivered");
        btnFilterPartial.setOnClickListener(v -> setFilter("completed"));
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        updateFilterButtonStyles();
        applyFilters();
    }

    private void updateFilterButtonStyles() {
        styleFilterButton(btnFilterAll, "all".equals(currentFilter));
        styleFilterButton(btnFilterActive, "active".equals(currentFilter));
        styleFilterButton(btnFilterUnfulfilled, "new".equals(currentFilter));
        styleFilterButton(btnFilterPartial, "completed".equals(currentFilter));
    }

    private void styleFilterButton(Button button, boolean selected) {
        if (button == null) return;
        if (selected) {
            button.setAlpha(1f);
            button.setEnabled(false);
        } else {
            button.setAlpha(0.85f);
            button.setEnabled(true);
        }
    }

    private void loadOrders() {
        String token = authManager.getBearerToken();
        String userId = authManager.getUserId();

        if (token == null || userId == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            swipeRefresh.setRefreshing(false);
            return;
        }

        // Only show skeleton on first load / when list is empty
        if (allOrders.isEmpty()) {
            showLoading(true);
        }
        tvEmpty.setVisibility(View.GONE);

        ApiService api = ApiClient.getApiService();
        api.getOrders(token, userId).enqueue(new Callback<OrdersResponse>() {
            @Override
            public void onResponse(Call<OrdersResponse> call, Response<OrdersResponse> response) {
                showLoading(false);
                swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    OrdersResponse body = response.body();
                    List<Order> orders = body.getData();

                    Log.d(TAG, "loadOrders success, count=" + (orders != null ? orders.size() : 0));

                    allOrders.clear();
                    if (orders != null) {
                        allOrders.addAll(orders);
                    }
                    applyFilters();

                    // Seed delivery progress so list chips match detail (cold start)
                    seedDeliveryProgress();

                    if (!allOrders.isEmpty()) {
                        recyclerView.setVisibility(View.VISIBLE);
                        tvEmpty.setVisibility(View.GONE);
                    } else {
                        recyclerView.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (response.code() == 401) {
                        authManager.refreshAccessToken(new AuthManager.TokenCallback() {
                            @Override
                            public void onToken(String accessToken) {
                                loadOrders(); // retry once with new token
                            }

                            @Override
                            public void onError(String message) {
                                showLoading(false);
                                swipeRefresh.setRefreshing(false);
                                Toast.makeText(OrdersActivity.this,
                                        "Session expired. Please login again.",
                                        Toast.LENGTH_LONG).show();
                                // Optional: go to login
                            }
                        });
                        return;
                    }
                    Toast.makeText(OrdersActivity.this,
                            "Failed to load orders (" + response.code() + ")",
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "loadOrders failed code=" + response.code());
                }
            }

            @Override
            public void onFailure(Call<OrdersResponse> call, Throwable t) {
                showLoading(false);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(OrdersActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Load orders failed", t);
            }
        });
    }

    private void showLoading(boolean show) {
        if (show) {
            shimmerLayout.setVisibility(View.VISIBLE);
            shimmerLayout.startShimmer();
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.GONE);
        } else {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
        }
    }

    private void updateOrderStatus(Order order, int newStatus) {
        String token = authManager.getBearerToken();
        if (token == null || order.getId() == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use swipeRefresh as indicator for status updates
        swipeRefresh.setRefreshing(true);

        Map<String, Object> body = new HashMap<>();
        body.put("id", order.getId());
        body.put("status", newStatus);

        ApiClient.getApiService().updateOrderStatus(token, body)
                .enqueue(new Callback<OrderUpdateResponse>() {
                    @Override
                    public void onResponse(Call<OrderUpdateResponse> call,
                                           Response<OrderUpdateResponse> response) {
                        swipeRefresh.setRefreshing(false);

                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getData() != null) {

                            Order updated = response.body().getData();

                            // Replace the single order in the local list
                            for (int i = 0; i < allOrders.size(); i++) {
                                if (allOrders.get(i).getId() != null
                                        && allOrders.get(i).getId().equals(updated.getId())) {
                                    allOrders.set(i, updated);
                                    break;
                                }
                            }

                            applyFilters();
                            Toast.makeText(OrdersActivity.this, "Order accepted", Toast.LENGTH_SHORT).show();
                        } else {
                            if (response.code() == 401) {
                                authManager.refreshAccessToken(new AuthManager.TokenCallback() {
                                    @Override
                                    public void onToken(String accessToken) {
                                        updateOrderStatus(order, newStatus);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        swipeRefresh.setRefreshing(false);
                                        Toast.makeText(OrdersActivity.this,
                                                "Session expired. Please login again.",
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                                return;
                            }
                            Toast.makeText(OrdersActivity.this,
                                    "Failed to update (" + response.code() + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<OrderUpdateResponse> call, Throwable t) {
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(OrdersActivity.this,
                                "Network error: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void applyFilters() {
        List<Order> filtered = new ArrayList<>();

        for (Order order : allOrders) {
            if (!matchesFilter(order)) continue;
            if (!matchesSearch(order)) continue;
            filtered.add(order);
        }

        adapter.setOrders(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesFilter(Order order) {
        if (order == null) return false;

        int status = order.getStatus();
        Integer deliveryStatus = DeliveryProgressStore.get().getByShopOrderId(order.getId());
        boolean isDelivered = status == 8
                || (deliveryStatus != null && deliveryStatus == 8);
        boolean isRejected = status == 11;

        switch (currentFilter) {
            case "new":
                return status == 2;

            case "active":
                // In progress for the shop, but not finished delivery
                if (isDelivered || isRejected) return false;
                return status >= 3 && status <= 7;

            case "completed":
                // UI label is "Delivered"
                return isDelivered;

            case "all":
            default:
                return true;
        }
    }

    private static boolean isDeliveredOrder(Order order) {
        if (order == null) return false;
        if (order.getStatus() == 8) return true;
        Integer ds = DeliveryProgressStore.get().getByShopOrderId(order.getId());
        return ds != null && ds == 8;
    }

    private boolean matchesSearch(Order order) {
        if (currentQuery == null || currentQuery.isEmpty()) {
            return true;
        }

        String id = order.getId() != null ? order.getId().toLowerCase(Locale.getDefault()) : "";
        String name = order.getCustomerDisplayName().toLowerCase(Locale.getDefault());
        String status = order.getStatusLabel().toLowerCase(Locale.getDefault());

        return id.contains(currentQuery)
                || name.contains(currentQuery)
                || status.contains(currentQuery);
    }

    private void openOrderDetail(Order order) {
        Intent intent = new Intent(OrdersActivity.this, OrderDetailActivity.class);
        intent.putExtra(OrderDetailActivity.EXTRA_ORDER_JSON, new Gson().toJson(order));
        startActivity(intent);
    }



    private void logout() {
        SocketManager.getInstance().disconnect();
        authManager.logout();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onOrderRequest(String orderJson) {
        Log.d(TAG, "order_request received");
        runOnUiThread(() -> {
            playNotificationSound();
            Toast.makeText(this, "New order received!", Toast.LENGTH_SHORT).show();

            // Force a visible refresh cue
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(true);
            }

            // Optional: parse and prepend if JSON is a full Order
            try {
                Order incoming = new Gson().fromJson(orderJson, Order.class);
                if (incoming != null && incoming.getId() != null) {
                    // Remove duplicate if already in list, then add at top
                    for (int i = allOrders.size() - 1; i >= 0; i--) {
                        if (incoming.getId().equals(allOrders.get(i).getId())) {
                            allOrders.remove(i);
                        }
                    }
                    allOrders.add(0, incoming);
                    applyFilters();
                    recyclerView.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);

                    // Immediate scroll for the prepended item
                    recyclerView.scrollToPosition(0);
                }
            } catch (Exception e) {
                Log.w(TAG, "parse order_request failed", e);
            }

            // Always refresh from API so list matches server
            Log.d(TAG, "calling loadOrders()");
            loadOrders();

            // Optional: scroll to top after load finishes (safety)
            recyclerView.post(() -> {
                if (adapter != null && adapter.getItemCount() > 0) {
                    recyclerView.scrollToPosition(0);
                }
            });
        });
    }

    @Override
    public void onDeliveryStatus(String payloadJson) {
        Log.d(TAG, "delivery_status: " + payloadJson);

        runOnUiThread(() -> {
            try {
                DeliveryStatusEvent event =
                        new Gson().fromJson(payloadJson, DeliveryStatusEvent.class);
                if (event == null) {
                    loadOrders();
                    return;
                }

                String shopOrderId = event.resolveShopOrderId();
                int ds = event.getStatus();

                if (shopOrderId != null && ds >= 2 && ds <= 8) {
                    DeliveryProgressStore.get().put(shopOrderId, event.getDeliveryId(), ds);
                }

                // UI feedback
                String label = event.getStatusLabel();
                Toast.makeText(this, label, Toast.LENGTH_SHORT).show();

                if (ds == 3 || ds == 8) {
                    playNotificationSound();
                }

                // Sync order status if provided
                if (event.getShopOrderStatus() != null && shopOrderId != null) {
                    patchOrderStatus(shopOrderId, event.getShopOrderStatus());
                }

                // Update list labels
                applyDeliveryStatusToList(event);

                // If delivered, refresh everything
                if (ds == 8) {
                    loadOrders();
                }
            } catch (Exception e) {
                Log.e(TAG, "delivery_status list", e);
                loadOrders();
            }
        });
    }

    private void applyDeliveryStatusToList(DeliveryStatusEvent event) {
        String shopOrderId = event.resolveShopOrderId();
        int ds = event.getStatus();

        if (shopOrderId != null && ds >= 2 && ds <= 8) {
            DeliveryProgressStore.get().put(shopOrderId, event.getDeliveryId(), ds);
        }

        applyFilters(); // refreshes adapter labels via notifyDataSetChanged
    }

    @Override
    public void onDriverLocation(String payloadJson) {
        // no-op on list
    }

    /** Update one order's status in memory if present, then re-filter. */
    private void patchOrderStatus(String orderId, int newStatus) {
        if (orderId == null) return;
        for (int i = 0; i < allOrders.size(); i++) {
            Order o = allOrders.get(i);
            if (orderId.equals(o.getId())) {
                o.setStatus(newStatus);
                allOrders.set(i, o);
                applyFilters();
                return;
            }
        }
    }

    private void playNotificationSound() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play sound", e);
        }
    }

    /**
     * Load recent business deliveries and map shop_order_id → delivery status
     * into DeliveryProgressStore so list chips show "Driver heading for pickup"
     * etc., not only shop order "Ready for Pickup".
     */
    private void seedDeliveryProgress() {
        String token = authManager.getBearerToken();
        String userId = authManager.getUserId();
        if (token == null || userId == null) return;

        // Only need progress for orders that may have a delivery
        boolean anyReadyOrBeyond = false;
        for (Order o : allOrders) {
            int s = o.getStatus();
            if (s >= 5 && s <= 8) {
                anyReadyOrBeyond = true;
                break;
            }
        }
        if (!anyReadyOrBeyond) return;

        String url = AppConfig.serverOrigin()
                + "/api/business/deliveries?uid="
                + Uri.encode(userId)
                + "&limit=50";

        ApiClient.getApiService().getBusinessDeliveries(token, url)
                .enqueue(new Callback<DeliveryListResponse>() {
                    @Override
                    public void onResponse(
                            Call<DeliveryListResponse> call,
                            Response<DeliveryListResponse> response) {

                        if (!response.isSuccessful()
                                || response.body() == null
                                || response.body().getData() == null) {
                            Log.w(TAG, "seedDeliveryProgress failed code=" + response.code());
                            return;
                        }

                        int seeded = 0;
                        for (Delivery d : response.body().getData()) {
                            if (d == null) continue;

                            String shopOrderId = d.getShopOrderId();
                            // Fallback if API nests shop_order
                            if ((shopOrderId == null || shopOrderId.isEmpty())
                                    && d.getShopOrder() != null) {
                                shopOrderId = d.getShopOrder().getId();
                            }

                            int ds = d.getStatus();
                            // Store only meaningful progress (store itself also filters 9–10)
                            if (shopOrderId != null && !shopOrderId.isEmpty()
                                    && ds >= 2 && ds <= 8) {
                                DeliveryProgressStore.get().put(
                                        shopOrderId,
                                        d.getId(),
                                        ds
                                );
                                seeded++;
                            }
                        }

                        Log.d(TAG, "seedDeliveryProgress seeded=" + seeded);
                        // Refresh chips without re-fetching orders
                        applyFilters();
                    }

                    @Override
                    public void onFailure(
                            Call<DeliveryListResponse> call,
                            Throwable t) {
                        Log.e(TAG, "seedDeliveryProgress network", t);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SocketManager.getInstance().removeOrderListener(this);
    }
}