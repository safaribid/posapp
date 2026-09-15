package com.safaribid.pos.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

public class PrintUtil {

    public static final int MAX_BIT_WIDTH = 384;

    /**
     * @param invert false = dark pixels print black (normal)
     *               true  = invert (try if paper is still blank)
     */
    public static byte[] getBitmapData(Bitmap bm) {
        return getBitmapData(bm, false);
    }

    public static byte[] getBitmapData(Bitmap bm, boolean invert) {
        final int srcWidth = Math.min(bm.getWidth(), MAX_BIT_WIDTH);
        final int dstWidth = getPaddingBitWidth(srcWidth);
        final int height = bm.getHeight();
        final int pitch = dstWidth / 8;
        final byte[] bits = new byte[pitch * height];

        if (height <= 0 || srcWidth <= 0) {
            return bits;
        }

        // Use a copy scaled/cropped to exact printer width if needed
        Bitmap work = bm;
        if (bm.getWidth() != srcWidth) {
            work = Bitmap.createBitmap(bm, 0, 0, srcWidth, height);
        }

        final int[] pixels = new int[srcWidth * height];
        work.getPixels(pixels, 0, srcWidth, 0, 0, srcWidth, height);

        for (int y = 0; y < height; y++) {
            final int rowOffset = y * pitch;
            final int pixelRowStart = y * srcWidth;

            for (int bytePos = 0; bytePos < pitch; bytePos++) {
                byte value = 0;
                final int startPixel = bytePos * 8;

                for (int bitPos = 0; bitPos < 8; bitPos++) {
                    final int x = startPixel + bitPos;
                    if (x >= srcWidth) break;

                    final int color = pixels[pixelRowStart + x];
                    final int r = (color >> 16) & 0xFF;
                    final int g = (color >> 8) & 0xFF;
                    final int b = color & 0xFF;
                    // luminance
                    final int lum = (r * 30 + g * 59 + b * 11) / 100;

                    // dark pixel → bit 1 (black on thermal)
                    boolean black = lum < 160; // slightly aggressive so gray text prints
                    if (invert) black = !black;

                    if (black) {
                        value |= (byte) (0x80 >> bitPos);
                    }
                }
                bits[rowOffset + bytePos] = value;
            }
        }

        return bits;
    }

    public static int getPaddingBitWidth(int width) {
        return ((width + 7) / 8) * 8;
    }

    /** Debug: solid black bar so you can verify the head prints anything */
    public static Bitmap solidBlackBar(int width, int height) {
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bm.eraseColor(Color.BLACK);
        return bm;
    }
}
