package com.safaribid.pos.printer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.safaribid.pos.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shows already-paired Bluetooth devices.
 * User selects one → MAC is saved and returned to the caller.
 */
public class PrinterPickerActivity extends AppCompatActivity {

    public static final String EXTRA_PRINTER_MAC = "printer_mac";
    public static final String EXTRA_PRINTER_NAME = "printer_name";

    private static final int REQ_BT_PERMISSIONS = 1001;

    private BluetoothAdapter bluetoothAdapter;
    private ListView listView;
    private TextView emptyView;
    private Button btnRefresh;
    private ArrayAdapter<String> adapter;
    private final List<BluetoothDevice> devices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer_picker);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Select Printer");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        listView = findViewById(R.id.listPrinters);
        emptyView = findViewById(R.id.txtEmpty);
        btnRefresh = findViewById(R.id.btnRefresh);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        listView.setOnItemClickListener(this::onDeviceClicked);
        btnRefresh.setOnClickListener(v -> loadPairedDevices());

        checkPermissionsAndLoad();
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };

            boolean allGranted = true;
            for (String p : perms) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                ActivityCompat.requestPermissions(this, perms, REQ_BT_PERMISSIONS);
                return;
            }
        }
        loadPairedDevices();
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
                loadPairedDevices();
            } else {
                Toast.makeText(this, "Bluetooth permission is required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void loadPairedDevices() {
        devices.clear();
        adapter.clear();

        if (bluetoothAdapter == null) {
            emptyView.setText("Bluetooth is not supported on this device");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            emptyView.setText("Please turn on Bluetooth first");
            Toast.makeText(this, "Bluetooth is turned off", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();

            if (bonded == null || bonded.isEmpty()) {
                emptyView.setText("No paired devices found.\n\nPair your printer in system Bluetooth settings first, then tap Refresh.");
                return;
            }

            for (BluetoothDevice device : bonded) {
                devices.add(device);
                String name = device.getName() != null ? device.getName() : "Unknown";
                adapter.add(name + "\n" + device.getAddress());
            }
            adapter.notifyDataSetChanged();

        } catch (SecurityException e) {
            Toast.makeText(this, "Missing Bluetooth permission", Toast.LENGTH_LONG).show();
        }
    }

    private void onDeviceClicked(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= devices.size()) return;

        BluetoothDevice device = devices.get(position);
        String mac = device.getAddress();
        String name = device.getName() != null ? device.getName() : "Printer";

        // Save as last used printer
        PrinterPrefs.saveLastPrinter(this, mac, name);

        Intent result = new Intent();
        result.putExtra(EXTRA_PRINTER_MAC, mac);
        result.putExtra(EXTRA_PRINTER_NAME, name);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}