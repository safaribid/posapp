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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
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

    private Toolbar toolbar;

    private TextView txtStatusChip, txtCreatedAt, txtTotalBig;
    private TextView txtCustomerName, txtCustomerEmail, txtCustomerPhone;
    private LinearLayout layoutOrderItems;
    private TextView txtEmptyItems;
    private TextView txtShippingAddress, txtShippingDetails;
    private TextView txtSubtotal, txtShippingCost, txtSummaryTotal, txtPaymentInfo, txtPaymentRef;

    private Button btnPrimaryAction;
    private Button btnPrint, btnTrackOrder;

    private ProgressBar progressBar;

    private Order currentOrder;
    private String orderId;
    private AuthManager authManager;

    private IPrinter printer;

    private final Gson gson = new Gson();

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
                currentOrder = gson.fromJson(orderJson, Order.class);
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
            updatePrimaryButton();
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

        btnPrimaryAction = findViewById(R.id.btnUpdateStatus);
        btnPrint = findViewById(R.id.btnPrint);
        btnTrackOrder = findViewById(R.id.btnTrackOrder);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
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
                    bindOrder(currentOrder);
                    updatePrimaryButton();
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

    private void updatePrimaryButton() {
        if (btnPrimaryAction == null || currentOrder == null) return;

        int status = currentOrder.getStatus();
        switch (status) {
            case 2:
                btnPrimaryAction.setText("Confirm Order");
                btnPrimaryAction.setEnabled(true);
                btnPrimaryAction.setVisibility(View.VISIBLE);
                break;
            case 3:
                btnPrimaryAction.setText("Start Preparing");
                btnPrimaryAction.setEnabled(true);
                btnPrimaryAction.setVisibility(View.VISIBLE);
                break;
            case 4:
                btnPrimaryAction.setText("Mark Ready");
                btnPrimaryAction.setEnabled(true);
                btnPrimaryAction.setVisibility(View.VISIBLE);
                break;
            case 5:
                btnPrimaryAction.setText("Ready for Pickup");
                btnPrimaryAction.setEnabled(true);
                btnPrimaryAction.setVisibility(View.VISIBLE);
                break;
            default:
                btnPrimaryAction.setText("Completed");
                btnPrimaryAction.setEnabled(false);
                btnPrimaryAction.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void onPrimaryActionClicked() {
        if (currentOrder == null) return;

        int status = currentOrder.getStatus();
        int nextStatus;
        switch (status) {
            case 2:
                nextStatus = 3;
                break;
            case 3:
                nextStatus = 4;
                break;
            case 4:
                nextStatus = 5;
                break;
            case 5:
                nextStatus = 5;
                break;
            default:
                return;
        }
        updateOrderStatus(nextStatus);
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
                        orderId = currentOrder.getId();
                    } else {
                        currentOrder.setStatus(newStatus);
                    }
                    bindOrder(currentOrder);
                    updatePrimaryButton();
                    Toast.makeText(OrderDetailActivity.this, "Status updated", Toast.LENGTH_SHORT).show();
                } else {
                    if (btnPrimaryAction != null) btnPrimaryAction.setEnabled(true);
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
        if (btnPrint != null) {
            btnPrint.setEnabled(false);
            btnPrint.setText("Connecting…");
        }

        IPrinter.ConnectionCallback callback = new IPrinter.ConnectionCallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    if (btnPrint != null) btnPrint.setText("Printing…");
                    doPrintReceipt();
                });
            }

            @Override
            public void onConnectionFailed(String error) {
                runOnUiThread(() -> {
                    if (btnPrint != null) {
                        btnPrint.setEnabled(true);
                        btnPrint.setText("Print Receipt");
                    }
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
                    if (btnPrint != null) {
                        btnPrint.setEnabled(true);
                        btnPrint.setText("Print Receipt");
                    }
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
                        if (btnPrint != null) {
                            btnPrint.setEnabled(true);
                            btnPrint.setText("Print Receipt");
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(OrderDetailActivity.this,
                                "Print failed: " + message, Toast.LENGTH_LONG).show();
                        if (btnPrint != null) {
                            btnPrint.setEnabled(true);
                            btnPrint.setText("Print Receipt");
                        }
                    });
                }
            });
        } catch (Exception e) {
            if (btnPrint != null) {
                btnPrint.setEnabled(true);
                btnPrint.setText("Print Receipt");
            }
            Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onTrackOrderClicked() {
        Toast.makeText(this, "Track Order – coming soon", Toast.LENGTH_SHORT).show();
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