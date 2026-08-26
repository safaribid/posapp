package com.safaribid.pos.printer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.safaribid.pos.models.Order;
import com.safaribid.pos.models.OrderItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Builds a receipt as a Bitmap so both SM1 (built-in) and
 * Bluetooth printers can use the same output.
 */
public class ReceiptBuilder {

    // 58mm printers ≈ 384 px wide. 80mm ≈ 576 px.
    private static final int RECEIPT_WIDTH = 384;
    private static final int PADDING = 12;
    private static final int LINE_SPACING = 6;

    private final Order order;

    private String shopName = "SafariBid";
    private String shopAddress = "";
    private String shopPhone = "";
    private String footer = "Thank you for ordering with SafariBid!";

    public ReceiptBuilder(Order order) {
        this.order = order;
    }

    public ReceiptBuilder setShopName(String shopName) {
        if (shopName != null && !shopName.trim().isEmpty()) {
            this.shopName = shopName.trim();
        }
        return this;
    }

    public ReceiptBuilder setShopAddress(String shopAddress) {
        this.shopAddress = shopAddress != null ? shopAddress.trim() : "";
        return this;
    }

    public ReceiptBuilder setShopPhone(String shopPhone) {
        this.shopPhone = shopPhone != null ? shopPhone.trim() : "";
        return this;
    }

    public ReceiptBuilder setFooter(String footer) {
        this.footer = footer != null ? footer.trim() : "";
        return this;
    }

    /**
     * Renders the full receipt into a Bitmap.
     */
    public Bitmap buildBitmap() {
        // First pass: measure height
        TextPaint titlePaint = createPaint(28, true);
        TextPaint normalPaint = createPaint(20, false);
        TextPaint boldPaint = createPaint(20, true);
        TextPaint smallPaint = createPaint(18, false);

        int contentWidth = RECEIPT_WIDTH - (PADDING * 2);
        int y = PADDING;

        // Measure sections
        y += measureText(shopName, titlePaint, contentWidth) + LINE_SPACING;
        if (!shopAddress.isEmpty()) {
            y += measureText(shopAddress, smallPaint, contentWidth) + 2;
        }
        if (!shopPhone.isEmpty()) {
            y += measureText(shopPhone, smallPaint, contentWidth) + 2;
        }
        y += 12; // separator

        y += measureText("Order #" + safe(order.getId()), boldPaint, contentWidth) + 4;
        y += measureText(order.getStatusLabel(), normalPaint, contentWidth) + 4;
        y += measureText(formatDate(order.getCreatedAt()), smallPaint, contentWidth) + 8;

        y += measureText("Customer: " + order.getCustomerDisplayName(), normalPaint, contentWidth) + 4;
        String phone = getPhone(order);
        if (!"-".equals(phone)) {
            y += measureText("Phone: " + phone, normalPaint, contentWidth) + 4;
        }
        if (order.getPaymentMethod() != null && !order.getPaymentMethod().isEmpty()) {
            y += measureText("Payment: " + order.getPaymentMethod(), normalPaint, contentWidth) + 4;
        }
        y += 12;

        // Items header
        y += measureText("Item                  Qty    Total", boldPaint, contentWidth) + 6;

        List<OrderItem> items = order.getItems();
        if (items != null) {
            for (OrderItem item : items) {
                String name = item.getProductTitle();
                if (name.length() > 18) name = name.substring(0, 17) + "…";
                String line = String.format(Locale.getDefault(), "%-18s %3d %8.2f",
                        name, item.getQuantity(), item.getPrice() * item.getQuantity());
                y += measureText(line, normalPaint, contentWidth) + 4;
            }
        }
        y += 10;

        y += measureText(String.format(Locale.getDefault(), "TOTAL: KES %.2f", order.getTotalPrice()),
                boldPaint, contentWidth) + 12;

        if (!footer.isEmpty()) {
            y += measureText(footer, smallPaint, contentWidth) + 8;
        }

        y += PADDING + 20; // bottom margin

        int height = Math.max(y, 200);

        // Second pass: draw
        Bitmap bitmap = Bitmap.createBitmap(RECEIPT_WIDTH, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        y = PADDING;

        y = drawCentered(canvas, shopName, titlePaint, contentWidth, y) + LINE_SPACING;
        if (!shopAddress.isEmpty()) {
            y = drawCentered(canvas, shopAddress, smallPaint, contentWidth, y) + 2;
        }
        if (!shopPhone.isEmpty()) {
            y = drawCentered(canvas, shopPhone, smallPaint, contentWidth, y) + 2;
        }

        y = drawSeparator(canvas, y) + 8;

        y = drawLeft(canvas, "Order #" + safe(order.getId()), boldPaint, y) + 4;
        y = drawLeft(canvas, order.getStatusLabel(), normalPaint, y) + 4;
        y = drawLeft(canvas, formatDate(order.getCreatedAt()), smallPaint, y) + 8;

        y = drawLeft(canvas, "Customer: " + order.getCustomerDisplayName(), normalPaint, y) + 4;
        if (!"-".equals(phone)) {
            y = drawLeft(canvas, "Phone: " + phone, normalPaint, y) + 4;
        }
        if (order.getPaymentMethod() != null && !order.getPaymentMethod().isEmpty()) {
            y = drawLeft(canvas, "Payment: " + order.getPaymentMethod(), normalPaint, y) + 4;
        }

        y = drawSeparator(canvas, y) + 8;

        y = drawLeft(canvas, "Item                  Qty    Total", boldPaint, y) + 6;

        if (items != null) {
            for (OrderItem item : items) {
                String name = item.getProductTitle();
                if (name.length() > 18) name = name.substring(0, 17) + "…";
                String line = String.format(Locale.getDefault(), "%-18s %3d %8.2f",
                        name, item.getQuantity(), item.getPrice() * item.getQuantity());
                y = drawLeft(canvas, line, normalPaint, y) + 4;
            }
        }

        y = drawSeparator(canvas, y) + 8;

        y = drawLeft(canvas, String.format(Locale.getDefault(), "TOTAL: KES %.2f", order.getTotalPrice()),
                boldPaint, y) + 12;

        if (!footer.isEmpty()) {
            drawCentered(canvas, footer, smallPaint, contentWidth, y);
        }

        return bitmap;
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    private TextPaint createPaint(float size, boolean bold) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(size);
        paint.setTypeface(bold
                ? Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                : Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        return paint;
    }

    private int measureText(String text, TextPaint paint, int width) {
        StaticLayout layout = new StaticLayout(
                text, paint, width,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f, 0.0f, false);
        return layout.getHeight();
    }

    private int drawLeft(Canvas canvas, String text, TextPaint paint, int y) {
        StaticLayout layout = new StaticLayout(
                text, paint, RECEIPT_WIDTH - (PADDING * 2),
                Layout.Alignment.ALIGN_NORMAL,
                1.0f, 0.0f, false);
        canvas.save();
        canvas.translate(PADDING, y);
        layout.draw(canvas);
        canvas.restore();
        return y + layout.getHeight();
    }

    private int drawCentered(Canvas canvas, String text, TextPaint paint, int contentWidth, int y) {
        StaticLayout layout = new StaticLayout(
                text, paint, contentWidth,
                Layout.Alignment.ALIGN_CENTER,
                1.0f, 0.0f, false);
        canvas.save();
        canvas.translate(PADDING, y);
        layout.draw(canvas);
        canvas.restore();
        return y + layout.getHeight();
    }

    private int drawSeparator(Canvas canvas, int y) {
        Paint linePaint = new Paint();
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(1.5f);
        int top = y + 4;
        canvas.drawLine(PADDING, top, RECEIPT_WIDTH - PADDING, top, linePaint);
        return top + 4;
    }

    private String safe(String value) {
        return (value == null || value.trim().isEmpty()) ? "-" : value.trim();
    }

    private String formatDate(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) {
            return new SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()).format(new Date());
        }
        // If backend already sends a readable string, just return it
        return createdAt;
    }

    private String getPhone(Order order) {
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
        return "-";
    }
}