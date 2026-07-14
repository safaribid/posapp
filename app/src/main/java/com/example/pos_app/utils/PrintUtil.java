package com.example.pos_app.utils;

import android.graphics.Bitmap;

import java.util.stream.IntStream;

public class PrintUtil {
    public static final int MAX_BIT_WIDTH = 384;

    /**
     * Converts a Bitmap to printer-compatible byte array
     */
    public static byte[] getBitmapData(Bitmap bm) {
        final int srcWidth = Math.min(bm.getWidth(), MAX_BIT_WIDTH);
        final int dstWidth = getPaddingBitWidth(srcWidth);
        final int height = bm.getHeight();
        final int pitch = dstWidth / 8;
        final byte[] bits = new byte[pitch * height];

        if (height <= 0 || srcWidth <= 0) {
            return bits;
        }

        final int[] pixels = new int[srcWidth * height];
        bm.getPixels(pixels, 0, srcWidth, 0, 0, srcWidth, height);

        IntStream.range(0, height).parallel().forEach(y -> {
            final int rowOffset = y * pitch;
            final int pixelRowStart = y * srcWidth;

            for (int bytePos = 0; bytePos < pitch; bytePos++) {
                final int startPixel = bytePos * 8;
                byte value = 0;

                for (int bitPos = 0; bitPos < 8; bitPos++) {
                    final int x = startPixel + bitPos;
                    if (x >= srcWidth) break;

                    final int color = pixels[pixelRowStart + x];

                    if ((color & 0x000000FF) < 128) {
                        value |= (0x80 >>> bitPos);
                    }
                }
                bits[rowOffset + bytePos] = value;
            }
        });

        return bits;
    }

    public static int getPaddingBitWidth(int width) {
        return ((width + 7) / 8) * 8;
    }
}