package com.safaribid.pos.ui.orders;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.gson.Gson;
import com.safaribid.pos.BaseActivity;
import com.safaribid.pos.R;
import com.safaribid.pos.auth.AuthManager;
import com.safaribid.pos.models.Delivery;
import com.safaribid.pos.models.DeliveryListResponse;
import com.safaribid.pos.models.DeliveryProgressStore;
import com.safaribid.pos.models.DeliveryStatusEvent;
import com.safaribid.pos.models.Order;
import com.safaribid.pos.models.OrderItem;
import com.safaribid.pos.models.OrderUpdateResponse;
import com.safaribid.pos.models.ShippingAddress;
import com.safaribid.pos.models.TrackDeliveryResponse;
import com.safaribid.pos.network.ApiClient;
import com.safaribid.pos.network.ApiService;
import com.safaribid.pos.network.SocketManager;
import com.safaribid.pos.printer.IPrinter;
import com.safaribid.pos.printer.PrinterFactory;
import com.safaribid.pos.printer.PrinterPickerActivity;
import com.safaribid.pos.printer.PrinterPrefs;
import com.safaribid.pos.printer.ReceiptBuilder;
import com.safaribid.pos.utils.AppConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderDetailActivity extends BaseActivity {

    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_ORDER_JSON = "order";

    private static final int REQ_BT_PERMISSIONS = 2001;

    private Toolbar toolbar;

    private TextView txtStatusChip, txtCreatedAt, txtTotalBig;
    private TextView txtCustomerName, txtCustomerEmail, txtCustomerPhone;
    private LinearLayout layoutOrderItems;
    private TextView txtEmptyItems;
    private TextView txtShippingAddress, txtShippingDetails;
    private TextView txtSubtotal, txtShippingCost, txtSummaryTotal, txtPaymentInfo, txtPaymentRef;

    private Button btnPrimaryAction;
    private Button btnAccept;
    private Button btnReject;
    private View layoutNewOrderActions;
    private Button btnPrint, btnTrackOrder;

    private ProgressBar progressBar;

    private Order currentOrder;
    private String orderId;
    private AuthManager authManager;

    private String lastTrackingCode;
    private String lastDeliveryId;
    private Integer lastDeliveryStatus; // from socket; null if unknown

    private IPrinter printer;

    private final Gson gson = new Gson();

    private final SocketManager.OrderListener detailSocketListener =
            new SocketManager.OrderListener() {
                @Override
                public void onOrderRequest(String orderJson) {
                    // ignore or reload if same id
                }

                @Override
                public void onDeliveryStatus(String payloadJson) {
                    runOnUiThread(() -> handleDeliveryStatusPayload(payloadJson));
                }

                @Override
                public void onDriverLocation(String payloadJson) {
                    // Not used in detail view, tracking has its own activity
                }
            };

    private final ActivityResultLauncher<Intent> printerPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String mac = result.getData()
                                    .getStringExtra(PrinterPickerActivity.EXTRA_PRINTER_MAC);
                            String name = result.getData()
                                    .getStringExtra(PrinterPickerActivity.EXTRA_PRINTER_NAME);
                            if (mac != null) {
                                PrinterPrefs.saveLastPrinter(this, mac, name);
                                PrinterFactory.setPreferredType(this, PrinterFactory.Type.BLUETOOTH);
                                printer = PrinterFactory.create(this, PrinterFactory.Type.BLUETOOTH);
                                // Retry print with explicit MAC
                                connectAndPrint(mac);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.brand_purple));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        applySystemBarInsets(findViewById(R.id.appBarLayout), findViewById(R.id.bottomActionBar));

        authManager = new AuthManager(this);

        if (!authManager.isLoggedIn()) {
            authManager.logoutAndRedirectToLogin(this);
            finish();
            return;
        }

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        String orderJson = getIntent().getStringExtra(EXTRA_ORDER_JSON);

        if (orderJson != null && !orderJson.isEmpty()) {
            try {
                currentOrder = gson.fromJson(orderJson, Order.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Critical: list often passes JSON only
        if (currentOrder != null && (orderId == null || orderId.isEmpty())) {
            orderId = currentOrder.getId();
        }

        if ((orderId == null || orderId.isEmpty()) && currentOrder == null) {
            Toast.makeText(this, "Missing order", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupClickListeners();

        PrinterFactory.setPreferredType(this, PrinterFactory.Type.BLUETOOTH);
        printer = PrinterFactory.create(this, PrinterFactory.Type.BLUETOOTH);

        if (currentOrder != null) {
            afterOrderBound();
        } else {
            loadOrder(orderId); // HTTP fetch — required for FCM path
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SocketManager.getInstance().addOrderListener(detailSocketListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (orderId != null) {
            // Optional: reload order so shopOrderStatus stays current after driver updates
            // loadOrder(orderId);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Hand listener back to list when leaving detail
        SocketManager.getInstance().removeOrderListener(detailSocketListener);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Order Detail");
        }

        txtStatusChip = findViewById(R.id.txtStatusChip);
        txtCreatedAt = findViewById(R.id.txtCreatedAt);
        txtTotalBig = findViewById(R.id.txtTotalBig);

        txtCustomerName = findViewById(R.id.txtCustomerName);
        txtCustomerEmail = findViewById(R.id.txtCustomerEmail);
        txtCustomerPhone = findViewById(R.id.txtCustomerPhone);

        layoutOrderItems = findViewById(R.id.layoutOrderItems);
        txtEmptyItems = findViewById(R.id.txtEmptyItems);

        txtShippingAddress = findViewById(R.id.txtShippingAddress);
        txtShippingDetails = findViewById(R.id.txtShippingDetails);

        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtShippingCost = findViewById(R.id.txtShippingCost);
        txtSummaryTotal = findViewById(R.id.txtSummaryTotal);
        txtPaymentInfo = findViewById(R.id.txtPaymentInfo);
        txtPaymentRef = findViewById(R.id.txtPaymentRef);

        btnPrimaryAction = findViewById(R.id.btnUpdateStatus);
        btnAccept = findViewById(R.id.btnAccept);
        btnReject = findViewById(R.id.btnReject);
        layoutNewOrderActions = findViewById(R.id.layoutNewOrderActions);

        btnPrint = findViewById(R.id.btnPrint);
        btnTrackOrder = findViewById(R.id.btnTrackOrder);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> updateOrderStatus(3)); // Accept → Confirmed
        }
        if (btnReject != null) {
            btnReject.setOnClickListener(v -> updateOrderStatus(11)); // Reject → status 11
        }
        if (btnPrimaryAction != null) {
            btnPrimaryAction.setOnClickListener(v -> onPrimaryActionClicked());
        }
        if (btnPrint != null) {
            btnPrint.setOnClickListener(v -> onPrintClicked());
        }
        if (btnTrackOrder != null) {
            btnTrackOrder.setOnClickListener(v -> onTrackOrderClicked());
        }
    }

    private void handleDeliveryStatusPayload(String payloadJson) {
        try {
            DeliveryStatusEvent event = gson.fromJson(payloadJson, DeliveryStatusEvent.class);
            if (event == null) return;

            String shopOrderId = event.resolveShopOrderId();
            int ds = event.getStatus();

            // Driver rejected / cancelled search step — do NOT lock UI or show Rejected
            if (ds == 10 || ds == 9) {
                Log.d("OrderDetail", "Ignoring driver-reject delivery status=" + ds);
                return;
            }

            // Update shared store
            if (shopOrderId != null && ds >= 2 && ds <= 8) {
                DeliveryProgressStore.get().put(shopOrderId, event.getDeliveryId(), ds);
            }

            // Sync order status if provided
            if (event.getShopOrderStatus() != null && shopOrderId != null) {
                patchOrderStatus(shopOrderId, event.getShopOrderStatus());
            }

            // If we know deliveryId, ignore other deliveries
            if (lastDeliveryId != null
                    && event.getDeliveryId() != null
                    && !lastDeliveryId.equals(event.getDeliveryId())) {
                return;
            }

            // Remember delivery id when we first see it
            if (event.getDeliveryId() != null) {
                lastDeliveryId = event.getDeliveryId();
            }

            lastDeliveryStatus = ds;

            // Always refresh chip + button from delivery progress
            applyDeliveryProgressToUi();

            Log.d("OrderDetail", "delivery_status applied status=" + lastDeliveryStatus
                    + " deliveryId=" + lastDeliveryId);
        } catch (Exception e) {
            Log.e("OrderDetail", "delivery_status parse error", e);
        }
    }

    private void applyDeliveryProgressToUi() {
        if (currentOrder == null) return;

        int orderStatus = currentOrder.getStatus();

        // Delivery labels only once order is ready for pickup or later
        boolean useDeliveryLabel = orderStatus >= 5
                && lastDeliveryStatus != null
                && lastDeliveryStatus >= 2
                && lastDeliveryStatus <= 8;

        if (useDeliveryLabel && txtStatusChip != null) {
            txtStatusChip.setText(
                    DeliveryProgressStore.labelFor(lastDeliveryStatus)
                            .toUpperCase(Locale.getDefault())
            );
        } else if (txtStatusChip != null) {
            txtStatusChip.setText(
                    currentOrder.getStatusLabel().toUpperCase(Locale.getDefault())
            );
        }

        updateActionButtons();
    }

    private void afterOrderBound() {
        if (currentOrder == null) return;

        bindOrder(currentOrder); // your existing bind

        if (currentOrder.getStatus() >= 5 && currentOrder.getStatus() < 9) {
            // Prefer known delivery id; else find by shop order id
            if (lastDeliveryId != null && !lastDeliveryId.isEmpty()) {
                fetchDeliveryAndApply(lastDeliveryId);
            } else {
                findDeliveryForShopOrder(currentOrder.getId());
            }
        } else {
            lastDeliveryStatus = null;
            updateActionButtons();
        }
    }

    private void fetchDeliveryAndApply(String deliveryId) {
        String token = authManager.getBearerToken();
        String uid = authManager.getUserId();
        if (token == null || uid == null) return;

        String url = AppConfig.serverOrigin()
                + "/api/business/deliveries/view?uid="
                + Uri.encode(uid)
                + "&id="
                + Uri.encode(deliveryId);

        ApiClient.getApiService().getBusinessDelivery(token, url)
                .enqueue(new Callback<TrackDeliveryResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TrackDeliveryResponse> call,
                                           @NonNull Response<TrackDeliveryResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            Delivery d = response.body().getData();
                            lastDeliveryId = d.getId();
                            lastDeliveryStatus = d.getStatus();

                            if (currentOrder != null) {
                                DeliveryProgressStore.get().put(currentOrder.getId(), d.getId(), d.getStatus());
                            }
                            
                            applyDeliveryProgressToUi();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TrackDeliveryResponse> call, @NonNull Throwable t) {
                    }
                });
    }

    private void findDeliveryForShopOrder(String shopOrderId) {
        String token = authManager.getBearerToken();
        String uid = authManager.getUserId();
        if (token == null || uid == null) return;

        String url = AppConfig.serverOrigin()
                + "/api/business/deliveries?uid="
                + Uri.encode(uid)
                + "&limit=50";

        ApiClient.getApiService().getBusinessDeliveries(token, url)
                .enqueue(new Callback<DeliveryListResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<DeliveryListResponse> call,
                                           @NonNull Response<DeliveryListResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            for (Delivery d : response.body().getData()) {
                                if (shopOrderId.equals(d.getShopOrderId())) {
                                    lastDeliveryId = d.getId();
                                    lastDeliveryStatus = d.getStatus();
                                    
                                    DeliveryProgressStore.get().put(shopOrderId, d.getId(), d.getStatus());
                                    
                                    applyDeliveryProgressToUi();
                                    return;
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<DeliveryListResponse> call, @NonNull Throwable t) {
                    }
                });
    }

    private void loadOrder(String id) {
        showLoading(true);

        String token = authManager.getBearerToken();
        if (token == null) {
            showLoading(false);
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService api = ApiClient.getApiService();
        Call<Order> call = api.getOrderById(token, id);

        call.enqueue(new Callback<Order>() {
            @Override
            public void onResponse(@NonNull Call<Order> call, @NonNull Response<Order> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    currentOrder = response.body();
                    orderId = currentOrder.getId();
                    afterOrderBound();
                } else {
                    if (response.code() == 401) {
                        authManager.refreshAccessToken(new AuthManager.TokenCallback() {
                            @Override
                            public void onToken(String accessToken) {
                                loadOrder(id);
                            }

                            @Override
                            public void onError(String message) {
                                showLoading(false);
                            }
                        });
                        return;
                    }
                    Toast.makeText(OrderDetailActivity.this, "Failed to load order", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Order> call, @NonNull Throwable t) {
                showLoading(false);
                Toast.makeText(OrderDetailActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindOrder(Order order) {
        if (order == null) return;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Order #" + safe(order.getId()));
        }

        txtStatusChip.setText(order.getStatusLabel().toUpperCase(Locale.getDefault()));
        txtCreatedAt.setText(safe(order.getCreatedAt()));
        txtTotalBig.setText(formatMoney(order.getTotalPrice()));

        txtCustomerName.setText(order.getCustomerDisplayName());

        String email = "—";
        if (order.getCustomer() != null
                && order.getCustomer().getEmail() != null
                && !order.getCustomer().getEmail().trim().isEmpty()) {
            email = order.getCustomer().getEmail().trim();
        }
        txtCustomerEmail.setText(email);
        txtCustomerPhone.setText(getCustomerPhone(order));

        layoutOrderItems.removeAllViews();
        List<OrderItem> items = order.getItems() != null ? order.getItems() : new ArrayList<>();

        if (items.isEmpty()) {
            if (txtEmptyItems != null) txtEmptyItems.setVisibility(View.VISIBLE);
            txtSubtotal.setText(formatMoney(0));
            txtShippingCost.setText(formatMoney(0));
        } else {
            if (txtEmptyItems != null) txtEmptyItems.setVisibility(View.GONE);
            double itemsTotal = 0;
            int index = 1;
            for (OrderItem item : items) {
                itemsTotal += item.getPrice() * item.getQuantity();
                addOrderItemRow(index++, item);
            }
            txtSubtotal.setText(formatMoney(itemsTotal));
            double shipping = Math.max(0, order.getTotalPrice() - itemsTotal);
            txtShippingCost.setText(formatMoney(shipping));
        }

        if (order.getShippingAddress() != null) {
            ShippingAddress sa = order.getShippingAddress();
            StringBuilder addr = new StringBuilder();
            if (sa.getAddress() != null && !sa.getAddress().trim().isEmpty()) {
                addr.append(sa.getAddress().trim());
            }
            if (sa.getCity() != null && !sa.getCity().trim().isEmpty()) {
                if (addr.length() > 0) addr.append(", ");
                addr.append(sa.getCity().trim());
            }
            txtShippingAddress.setText(addr.length() > 0 ? addr.toString() : "—");

            if (sa.getDetails() != null && !sa.getDetails().trim().isEmpty()) {
                txtShippingDetails.setVisibility(View.VISIBLE);
                txtShippingDetails.setText(sa.getDetails().trim());
            } else {
                txtShippingDetails.setVisibility(View.GONE);
            }
        } else {
            txtShippingAddress.setText("—");
            txtShippingDetails.setVisibility(View.GONE);
        }

        txtSummaryTotal.setText(formatMoney(order.getTotalPrice()));

        String method = order.getPaymentMethod() != null ? order.getPaymentMethod() : "—";
        String payStatus = order.getPaymentStatus() != null ? order.getPaymentStatus() : "";
        txtPaymentInfo.setText(method + (payStatus.isEmpty() ? "" : " • " + payStatus));

        String ref = order.getPaymentReference();
        txtPaymentRef.setText(ref != null && !ref.trim().isEmpty() ? "Ref: " + ref.trim() : "Ref: —");

        // Chip text after bind:
        applyDeliveryProgressToUi();
    }

    private void addOrderItemRow(int index, OrderItem item) {
        TextView tv = new TextView(this);
        tv.setTextColor(0xFF424242);
        tv.setTextSize(14f);
        tv.setLineSpacing(4f, 1f);

        String name = item.getProductTitle();
        String line = index + ". " + item.getQuantity() + " × " + name
                + "\n     " + formatMoney(item.getPrice() * item.getQuantity());
        tv.setText(line);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (index == 1) ? 0 : 10;
        layoutOrderItems.addView(tv, lp);
    }

    private String getCustomerPhone(Order order) {
        if (order.getCustomer() != null
                && order.getCustomer().getPhone() != null
                && !order.getCustomer().getPhone().trim().isEmpty()) {
            return order.getCustomer().getPhone().trim();
        }
        if (order.getShippingAddress() != null
                && order.getShippingAddress().getPhone() != null
                && !order.getShippingAddress().getPhone().trim().isEmpty()) {
            return order.getShippingAddress().getPhone().trim();
        }
        return "—";
    }

    private void updateActionButtons() {
        if (currentOrder == null) return;

        int status = currentOrder.getStatus();

        if (layoutNewOrderActions != null) {
            boolean isNew = status == 2;
            layoutNewOrderActions.setVisibility(isNew ? View.VISIBLE : View.GONE);
        }

        if (btnPrimaryAction == null) return;

        if (status == 2) {
            btnPrimaryAction.setVisibility(View.GONE);
            return;
        }

        // Lock only after accept (3) through delivered (8)
        boolean driverLocked = lastDeliveryStatus != null
                && lastDeliveryStatus >= 3
                && lastDeliveryStatus <= 8;

        if (driverLocked) {
            btnPrimaryAction.setVisibility(View.VISIBLE);
            btnPrimaryAction.setText(DeliveryProgressStore.labelFor(lastDeliveryStatus));
            btnPrimaryAction.setEnabled(false);
            if (txtStatusChip != null) {
                txtStatusChip.setText(DeliveryProgressStore.labelFor(lastDeliveryStatus).toUpperCase(Locale.getDefault()));
            }
            return;
        }

        btnPrimaryAction.setVisibility(View.VISIBLE);

        switch (status) {
            case 3:
                btnPrimaryAction.setText("Start Preparing");
                btnPrimaryAction.setEnabled(true);
                break;
            case 4:
                btnPrimaryAction.setText("Mark Ready");
                btnPrimaryAction.setEnabled(true);
                break;
            case 5:
                btnPrimaryAction.setText("Request Driver");
                btnPrimaryAction.setEnabled(true); // can press again until driver accepts
                break;
            case 8:
                btnPrimaryAction.setText("Delivered");
                btnPrimaryAction.setEnabled(false);
                break;
            case 9:
            case 10:
            case 11:
                btnPrimaryAction.setText("Rejected");
                btnPrimaryAction.setEnabled(false);
                break;
            default:
                btnPrimaryAction.setText(currentOrder.getStatusLabel());
                btnPrimaryAction.setEnabled(false);
                break;
        }
    }

    private void onPrimaryActionClicked() {
        if (currentOrder == null) return;
        if (lastDeliveryStatus != null && lastDeliveryStatus >= 3) return;

        int status = currentOrder.getStatus();
        switch (status) {
            case 3:
                updateOrderStatus(4); // Start Preparing
                break;
            case 4:
                updateOrderStatus(5); // Mark Ready → Ready for Pickup (+ first dispatch)
                break;
            case 5:
                updateOrderStatus(5); // Request Driver again
                break;
            default:
                break;
        }
    }

    private void patchOrderStatus(String orderId, int newStatus) {
        if (currentOrder != null && orderId != null && orderId.equals(currentOrder.getId())) {
            currentOrder.setStatus(newStatus);
            runOnUiThread(() -> bindOrder(currentOrder));
        }
    }

    private void updateOrderStatus(int newStatus) {
        if (currentOrder == null || orderId == null) return;

        showLoading(true);
        if (btnPrimaryAction != null) btnPrimaryAction.setEnabled(false);

        String token = authManager.getBearerToken();
        if (token == null) {
            showLoading(false);
            if (btnPrimaryAction != null) btnPrimaryAction.setEnabled(true);
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("id", orderId);
        body.put("status", newStatus);

        ApiService api = ApiClient.getApiService();
        Call<OrderUpdateResponse> call = api.updateOrderStatus(token, body);

        call.enqueue(new Callback<OrderUpdateResponse>() {
            @Override
            public void onResponse(@NonNull Call<OrderUpdateResponse> call,
                                   @NonNull Response<OrderUpdateResponse> response) {
                showLoading(false);

                if (response.isSuccessful()) {
                    if (response.body() != null && response.body().getData() != null) {
                        currentOrder = response.body().getData();
                        if (currentOrder.getId() != null) {
                            orderId = currentOrder.getId();
                        }
                    } else if (currentOrder != null) {
                        currentOrder.setStatus(newStatus);
                    }

                    // Capture tracking code if provided
                    if (response.body() != null && response.body().getDelivery() != null) {
                        Delivery d = response.body().getDelivery();
                        lastTrackingCode = d.getTrackingCode();
                        lastDeliveryId = d.getId();
                        
                        DeliveryProgressStore.get().put(orderId, d.getId(), d.getStatus());
                    }

                    bindOrder(currentOrder);
                    updateActionButtons();
                    Toast.makeText(OrderDetailActivity.this, "Status updated", Toast.LENGTH_SHORT).show();
                } else {
                    if (response.code() == 401) {
                        authManager.refreshAccessToken(new AuthManager.TokenCallback() {
                            @Override
                            public void onToken(String accessToken) {
                                updateOrderStatus(newStatus);
                            }

                            @Override
                            public void onError(String message) {
                                showLoading(false);
                            }
                        });
                        return;
                    }
                    if (btnPrimaryAction != null) btnPrimaryAction.setEnabled(true);
                    updateActionButtons(); // restore correct enabled state
                    Toast.makeText(OrderDetailActivity.this,
                            "Failed to update status (" + response.code() + ")",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<OrderUpdateResponse> call, @NonNull Throwable t) {
                showLoading(false);
                if (btnPrimaryAction != null) btnPrimaryAction.setEnabled(true);
                Toast.makeText(OrderDetailActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onPrintClicked() {
        if (currentOrder == null) {
            Toast.makeText(this, "No order to print", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission();
            return;
        }
        // Auto path first (saved MAC or discover)
        connectAndPrint(null);
    }

    /**
     * @param mac null = auto connect; non-null = explicit after manual pick
     */
    private void connectAndPrint(String mac) {
        if (printer == null) {
            printer = PrinterFactory.create(this, PrinterFactory.Type.BLUETOOTH);
        }

        Toast.makeText(this, "Connecting to printer…", Toast.LENGTH_SHORT).show();

        IPrinter.ConnectionCallback cb = new IPrinter.ConnectionCallback() {
            @Override
            public void onConnected() {
                doPrintReceipt();
            }

            @Override
            public void onConnectionFailed(String error) {
                Toast.makeText(OrderDetailActivity.this,
                        "Auto connect failed. Select printer…",
                        Toast.LENGTH_LONG).show();
                openPrinterPicker();
            }

            @Override
            public void onDisconnected() {
            }
        };

        if (mac != null && !mac.isEmpty()) {
            printer.connect(mac, cb);
        } else {
            printer.connect(cb); // auto
        }
    }

    private void doPrintReceipt() {
        try {
            Bitmap receiptBitmap = new ReceiptBuilder(currentOrder)
                    .setShopName(AppConfig.SHOP_NAME)
                    .setShopAddress(AppConfig.SHOP_ADDRESS)
                    .setShopPhone(AppConfig.SHOP_PHONE)
                    .setFooter("Thank you for ordering with SafariBid!")
                    .buildBitmap();

            printer.printBitmap(receiptBitmap, new IPrinter.PrintCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(OrderDetailActivity.this,
                            "Receipt printed", Toast.LENGTH_SHORT).show();
                    printer.disconnect();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(OrderDetailActivity.this,
                            "Print error: " + message, Toast.LENGTH_LONG).show();
                    printer.disconnect();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Print prepare failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openPrinterPicker() {
        Intent intent = new Intent(this, PrinterPickerActivity.class);
        printerPickerLauncher.launch(intent);
    }

    private void onTrackOrderClicked() {
        if (currentOrder == null || currentOrder.getId() == null) {
            Toast.makeText(this, "No order loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, TrackOrderActivity.class);
        intent.putExtra(TrackOrderActivity.EXTRA_SHOP_ORDER_ID, currentOrder.getId());
        startActivity(intent);
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    },
                    REQ_BT_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_BT_PERMISSIONS) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                onPrintClicked();
            } else {
                Toast.makeText(this, "Bluetooth permission is required to print", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private String safe(String value) {
        return (value == null || value.trim().isEmpty()) ? "—" : value.trim();
    }

    private String formatMoney(double amount) {
        if (amount == (long) amount) {
            return String.format(Locale.getDefault(), "KES %d", (long) amount);
        }
        return String.format(Locale.getDefault(), "KES %.2f", amount);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (printer != null) {
            printer.disconnect();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}