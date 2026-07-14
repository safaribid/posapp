package com.example.pos_app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.sdk.api.printer.IPrinterBinderService;
import com.android.sdk.api.printer.IPrinterCallback;
import com.example.pos_app.utils.PrintUtil;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnPrint;
    private IPrinterBinderService printerService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnPrint = findViewById(R.id.btn_print);

        tvStatus.setText("Hello World POS Demo\nPreparing SDK...");

        bindSdkServices();

        btnPrint.setOnClickListener(v -> printHelloReceipt());
    }

    private void bindSdkServices() {
        // Simplified for now - full binding will be added later
        tvStatus.setText("✅ App Ready!\n" +
                "1. Make sure the two service APKs are installed on the SM1 device.\n" +
                "2. Click the button below to print.");

        printerService = null; // Will be set after proper binding
    }

    private void printHelloReceipt() {
        if (printerService == null) {
            Toast.makeText(this, "Printer service not connected yet.\nInstall service APKs.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Bitmap receipt = createReceiptBitmap();
            if (receipt == null) {
                Toast.makeText(this, "Failed to create receipt image (out of memory)", Toast.LENGTH_SHORT).show();
                return;
            }
            byte[] printData = PrintUtil.getBitmapData(receipt);

            // Correct signature based on typical SUNTEK SDK
            printerService.printBitmap(0, printData, 0, 0, new IPrinterCallback.Stub() {
                @Override
                public void onResult(int resultCode, String message, int extra) throws RemoteException {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Print Result: " + resultCode + "\n" + message,
                                    Toast.LENGTH_LONG).show());
                }
            });
        } catch (OutOfMemoryError e) {
            Toast.makeText(this, "Out of memory: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap createReceiptBitmap() {
        int width = 384;
        int height = 280;
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return null;
        }
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
                "Thank you for testing!",
                "",
                "Date: " + new java.util.Date()
        };

        int y = 40;
        for (String line : lines) {
            canvas.drawText(line, 30, y, paint);
            y += 35;
        }

        return bitmap;
    }
}