package com.safaribid.pos.ui.orders;

import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.safaribid.pos.R;
import com.safaribid.pos.auth.AuthManager;
import com.safaribid.pos.models.Order;
import com.safaribid.pos.models.OrderUpdateResponse;
import com.safaribid.pos.models.OrdersResponse;
import com.safaribid.pos.network.ApiClient;
import com.safaribid.pos.network.ApiService;
import com.safaribid.pos.network.SocketManager;

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
    private com.facebook.shimmer.ShimmerFrameLayout shimmerLayout;
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
    private String currentFilter = "all"; // all | active | unfulfilled | partial

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders);

        authManager = new AuthManager(this);

        if (!authManager.isLoggedIn()) {
            finish();
            return;
        }

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
                startActivity(new Intent(this, com.safaribid.pos.printer.PrinterPickerActivity.class));
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
                // Confirm reject status code with backend
                Toast.makeText(OrdersActivity.this,
                        "Reject status code to be confirmed with backend",
                        Toast.LENGTH_SHORT).show();
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
        SocketManager.getInstance().setOrderListener(this);
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

        loadOrders();
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
        btnFilterUnfulfilled.setOnClickListener(v -> setFilter("unfulfilled"));
        btnFilterPartial.setOnClickListener(v -> setFilter("partial"));
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        updateFilterButtonStyles();
        applyFilters();
    }

    private void updateFilterButtonStyles() {
        styleFilterButton(btnFilterAll, "all".equals(currentFilter));
        styleFilterButton(btnFilterActive, "active".equals(currentFilter));
        styleFilterButton(btnFilterUnfulfilled, "unfulfilled".equals(currentFilter));
        styleFilterButton(btnFilterPartial, "partial".equals(currentFilter));
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
        int status = order.getStatus();

        switch (currentFilter) {
            case "unfulfilled":
                // New orders before accept/reject
                return status == 2;
            case "partial":
                // Preparing / ready
                return status == 3 || status == 4;
            case "active":
                // Everything not completed/cancelled
                return status != 5 && status != 6;
            case "all":
            default:
                return true;
        }
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

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void logout() {
        SocketManager.getInstance().disconnect();
        authManager.logout();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, com.safaribid.pos.auth.LoginActivity.class);
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
        // P3 — log only for now
        Log.d(TAG, "delivery_status: " + payloadJson);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SocketManager.getInstance().setOrderListener(null);
    }
}