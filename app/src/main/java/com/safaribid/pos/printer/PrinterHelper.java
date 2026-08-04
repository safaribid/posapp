package com.safaribid.pos.printer;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Toast;

import com.safaribid.pos.utils.PrintUtil;

public class PrinterHelper {

    private final Context context;

    public PrinterHelper(Context context) {
        this.context = context;
    }

    public void printReceipt(Bitmap receiptBitmap) {
        if (receiptBitmap == null) {
            Toast.makeText(context, "Receipt image is null", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            byte[] printData = PrintUtil.getBitmapData(receiptBitmap);
            // TODO: Call your existing IPrinterBinderService here
            Toast.makeText(context, "Print data prepared (" + printData.length + " bytes)", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Print error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}