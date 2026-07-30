package com.example.pos_app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.sdk.api.ISdkServiceManager;
import com.android.sdk.api.constant.ServiceID;
import com.android.sdk.api.printer.IPrinterBinderService;
import com.android.sdk.api.printer.IPrinterCallback;
import com.example.pos_app.utils.PrintUtil;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnPrint;
    private IPrinterBinderService printerService;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                ISdkServiceManager manager = ISdkServiceManager.Stub.asInterface(service);
                printerService = (IPrinterBinderService) manager.getService(ServiceID.SERVICE_ID_PRINTER);
                tvStatus.setText(" Printer Connected Successfully!\nTap button to print 'Hello World'.");
            } catch (Exception e) {
                tvStatus.setText("Binding error: " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            printerService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnPrint = findViewById(R.id.btn_print);

        tvStatus.setText("Connecting to Printer Service...");

        bindPrinterService();

        btnPrint.setOnClickListener(v -> printHelloReceipt());
    }

    private void bindPrinterService() {
        try {
            Intent intent = new Intent();
            intent.setAction("com.android.sdk.service");
            intent.setPackage("com.android.sdk.service");
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            tvStatus.setText("Failed to bind: " + e.getMessage());
        }
    }

    private void printHelloReceipt() {
        if (printerService == null) {
            Toast.makeText(this, "Printer not connected yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Bitmap receipt = createReceiptBitmap();
            byte[] printData = PrintUtil.getBitmapData(receipt);

            printerService.printBitmap(0, printData, 0, 0, new IPrinterCallback.Stub() {
                @Override
                public void onResult(int resultCode, String message, int extra) throws RemoteException {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Print: " + resultCode + " - " + message, Toast.LENGTH_LONG).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Print error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap createReceiptBitmap() {
        int width = 384;
        int height = 280;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(28);
        paint.setAntiAlias(true);

        String[] lines = {
                "Hello World",
                "POS Receipt Demo",
                "----------------",
                "Thank you!",
                "Date: " + new java.util.Date()
        };

        int y = 40;
        for (String line : lines) {
            canvas.drawText(line, 30, y, paint);
            y += 35;
        }

        return bitmap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnection != null) {
            unbindService(serviceConnection);
        }
    }
}