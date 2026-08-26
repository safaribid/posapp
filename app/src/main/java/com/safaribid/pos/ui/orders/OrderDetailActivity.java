package com.safaribid.pos.ui.orders;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.safaribid.pos.R;
import com.safaribid.pos.auth.AuthManager;
import com.safaribid.pos.models.Order;
import com.safaribid.pos.models.OrderItem;
import com.safaribid.pos.models.OrderUpdateResponse;
import com.safaribid.pos.models.ShippingAddress;
import com.safaribid.pos.network.ApiClient;
import com.safaribid.pos.network.ApiService;
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

public class OrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_ORDER_JSON = "order";

    private static final int REQ_BT_PERMISSIONS = 2001;

    // Toolbar
    private Toolbar toolbar;

    // Header card
    private TextView txtStatusChip, txtCreatedAt, txtTotalBig;

    // Customer
    private TextView txtCustomerName, txtCustomerEmail, txtCustomerPhone;

    // Items
    private LinearLayout layoutOrderItems;
    private TextView txtEmptyItems;

    // Shipping
    private TextView txtShippingAddress, txtShippingDetails;

    // Summary
    private TextView txtSubtotal, txtShippingCost, txtSummaryTotal, txtPaymentInfo, txtPaymentRef;

    // Bottom buttons
    private Button btnUpdateStatus, btnPrint, btnTrackOrder;

    private ProgressBar progressBar;

    // Data
    private Order currentOrder;
    private String orderId;
    private AuthManager authManager;

    // Printer (SM1 + Bluetooth)
    private IPrinter printer;

    private final ActivityResultLauncher<Intent> printerPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String mac = result.getData().getStringExtra(PrinterPickerActivity.EXTRA_PRINTER_MAC);
                    String name = result.getData().getStringExtra(PrinterPickerActivity.EXTRA_PRINTER_NAME);

                    if (mac != null && !mac.isEmpty()) {
                        Toast.makeText(this, "Selected: " + (name != null ? name : mac), Toast.LENGTH_SHORT).show();
                        PrinterFactory.setPreferredType(this, PrinterFactory.Type.EXTERNAL_BLUETOOTH);
                        printer = PrinterFactory.create(this, PrinterFactory.Type.EXTERNAL_BLUETOOTH);
                        connectAndPrint(mac);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        authManager = new AuthManager(this);

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        String orderJson = getIntent().getStringExtra(EXTRA_ORDER_JSON);
        if (orderJson != null && !orderJson.isEmpty()) {
            try {
                currentOrder = new com.google.gson.Gson().fromJson(orderJson, Order.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (orderId == null && currentOrder == null) {
            Toast.makeText(this, "Missing order", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (currentOrder != null && orderId == null) {
            orderId = currentOrder.getId();
        }

        bindViews();
        setupClickListeners();

        printer = PrinterFactory.createDefault(this);

        if (currentOrder != null) {
            bindOrder(currentOrder);
        } else {
            loadOrder(orderId);
        }
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

        btnUpdateStatus = findViewById(R.id.btnUpdateStatus);
        btnPrint = findViewById(R.id.btnPrint);
        btnTrackOrder = findViewById(R.id.btnTrackOrder);

        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
        btnUpdateStatus.setOnClickListener(v -> showStatusPicker());
        btnPrint.setOnClickListener(v -> onPrintClicked());
        btnTrackOrder.setOnClickListener(v -> onTrackOrderClicked());
    }

    // ------------------------------------------------------------------
    // Load order
    // ------------------------------------------------------------------

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
                    bindOrder(currentOrder);
                } else {
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

    // ------------------------------------------------------------------
    // Bind UI (matches screenshot layout)
    // ------------------------------------------------------------------

    private void bindOrder(Order order) {
        if (order == null) return;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Order #" + safe(order.getId()));
        }

        // Status chip
        txtStatusChip.setText(order.getStatusLabel().toUpperCase(Locale.getDefault()));

        // Date
        txtCreatedAt.setText(safe(order.getCreatedAt()));

        // Big total
        txtTotalBig.setText(formatMoney(order.getTotalPrice()));

        // Customer
        txtCustomerName.setText(order.getCustomerDisplayName());

        String email = "—";
        if (order.getCustomer() != null && order.getCustomer().getEmail() != null
                && !order.getCustomer().getEmail().trim().isEmpty()) {
            email = order.getCustomer().getEmail().trim();
        }
        txtCustomerEmail.setText(email);
        txtCustomerPhone.setText(getCustomerPhone(order));

        // Order items (numbered list)
        layoutOrderItems.removeAllViews();
        List<OrderItem> items = order.getItems() != null ? order.getItems() : new ArrayList<>();

        if (items.isEmpty()) {
            txtEmptyItems.setVisibility(View.VISIBLE);
            txtSubtotal.setText(formatMoney(0));
            txtShippingCost.setText(formatMoney(0));
        } else {
            txtEmptyItems.setVisibility(View.GONE);
            double itemsTotal = 0;
            int index = 1;
            for (OrderItem item : items) {
                itemsTotal += item.getPrice() * item.getQuantity();
                addOrderItemRow(index++, item);
            }
            txtSubtotal.setText(formatMoney(itemsTotal));

            // Approximate shipping until real field exists
            double shipping = Math.max(0, order.getTotalPrice() - itemsTotal);
            txtShippingCost.setText(formatMoney(shipping));
        }

        // Shipping information
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
            if (sa.getName() != null && !sa.getName().trim().isEmpty() && addr.length() == 0) {
                addr.append(sa.getName().trim());
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

        // Summary
        txtSummaryTotal.setText(formatMoney(order.getTotalPrice()));

        String method = order.getPaymentMethod() != null ? order.getPaymentMethod() : "—";
        String payStatus = order.getPaymentStatus() != null ? order.getPaymentStatus() : "";
        txtPaymentInfo.setText(method + (payStatus.isEmpty() ? "" : " • " + payStatus));

        String ref = order.getPaymentReference();
        txtPaymentRef.setText(ref != null && !ref.trim().isEmpty() ? "Ref: " + ref.trim() : "Ref: —");
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

    // ------------------------------------------------------------------
    // Status update
    // ------------------------------------------------------------------

    private void showStatusPicker() {
        if (currentOrder == null) return;

        final String[] labels = {
                "Pending",
                "New Order",
                "Confirmed",
                "Preparing",
                "Ready for Pickup",
                "Driver on the way",
                "Driver is here",
                "Rejected"
        };
        final int[] codes = {1, 2, 3, 4, 5, 6, 7, 9};

        new AlertDialog.Builder(this)
                .setTitle("Update Status")
                .setItems(labels, (dialog, which) -> updateOrderStatus(codes[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateOrderStatus(int newStatus) {
        if (currentOrder == null || orderId == null) return;

        showLoading(true);
        btnUpdateStatus.setEnabled(false);

        String token = authManager.getBearerToken();
        if (token == null) {
            showLoading(false);
            btnUpdateStatus.setEnabled(true);
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
                btnUpdateStatus.setEnabled(true);

                if (response.isSuccessful()) {
                    currentOrder.setStatus(newStatus);
                    bindOrder(currentOrder);
                    Toast.makeText(OrderDetailActivity.this, "Status updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(OrderDetailActivity.this, "Failed to update status", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<OrderUpdateResponse> call, @NonNull Throwable t) {
                showLoading(false);
                btnUpdateStatus.setEnabled(true);
                Toast.makeText(OrderDetailActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // Printing (SM1 + Bluetooth)
    // ------------------------------------------------------------------

    private void onPrintClicked() {
        if (currentOrder == null) {
            Toast.makeText(this, "Order not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        PrinterFactory.Type type = PrinterFactory.getPreferredType(this);

        if (type == PrinterFactory.Type.EXTERNAL_BLUETOOTH) {
            if (!hasBluetoothPermission()) {
                requestBluetoothPermission();
                return;
            }

            String lastMac = PrinterPrefs.getLastMac(this);
            if (lastMac != null && !lastMac.isEmpty()) {
                connectAndPrint(lastMac);
            } else {
                openPrinterPicker();
            }
        } else {
            // Built-in SM1
            connectAndPrint(null);
        }
    }

    private void openPrinterPicker() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission();
            return;
        }
        Intent intent = new Intent(this, PrinterPickerActivity.class);
        printerPickerLauncher.launch(intent);
    }

    private void connectAndPrint(String mac) {
        btnPrint.setEnabled(false);
        btnPrint.setText("Connecting…");

        IPrinter.ConnectionCallback callback = new IPrinter.ConnectionCallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    btnPrint.setText("Printing…");
                    doPrintReceipt();
                });
            }

            @Override
            public void onConnectionFailed(String error) {
                runOnUiThread(() -> {
                    btnPrint.setEnabled(true);
                    btnPrint.setText("Print Receipt");
                    Toast.makeText(OrderDetailActivity.this,
                            "Could not connect: " + error, Toast.LENGTH_LONG).show();

                    if (PrinterFactory.getPreferredType(OrderDetailActivity.this)
                            == PrinterFactory.Type.EXTERNAL_BLUETOOTH) {
                        openPrinterPicker();
                    }
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    btnPrint.setEnabled(true);
                    btnPrint.setText("Print Receipt");
                });
            }
        };

        if (mac != null) {
            printer.connect(mac, callback);
        } else {
            printer.connect(callback);
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
                    runOnUiThread(() -> {
                        Toast.makeText(OrderDetailActivity.this, "Receipt printed", Toast.LENGTH_SHORT).show();
                        btnPrint.setEnabled(true);
                        btnPrint.setText("Print Receipt");
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(OrderDetailActivity.this,
                                "Print failed: " + message, Toast.LENGTH_LONG).show();
                        btnPrint.setEnabled(true);
                        btnPrint.setText("Print Receipt");
                    });
                }
            });
        } catch (Exception e) {
            btnPrint.setEnabled(true);
            btnPrint.setText("Print Receipt");
            Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------
    // Track Order
    // ------------------------------------------------------------------

    private void onTrackOrderClicked() {
        // TODO: replace with real tracking screen / URL / map
        Toast.makeText(this, "Track Order – coming soon", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private String safe(String value) {
        return (value == null || value.trim().isEmpty()) ? "—" : value.trim();
    }

    private String formatMoney(double amount) {
        // Match screenshot style: "KES 116" (no decimals if whole number looks cleaner)
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