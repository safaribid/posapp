package com.safaribid.pos.printer;

/**
 * Common ESC/POS commands for thermal printers (58mm / 80mm).
 */
public final class EscPosCommands {

    private EscPosCommands() {}

    // Initialize printer
    public static final byte[] INIT = {0x1B, 0x40};                 // ESC @

    // Alignment
    public static final byte[] ALIGN_LEFT   = {0x1B, 0x61, 0x00};   // ESC a 0
    public static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};   // ESC a 1
    public static final byte[] ALIGN_RIGHT  = {0x1B, 0x61, 0x02};   // ESC a 2

    // Text style
    public static final byte[] BOLD_ON  = {0x1B, 0x45, 0x01};       // ESC E 1
    public static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};       // ESC E 0
    public static final byte[] UNDERLINE_ON  = {0x1B, 0x2D, 0x01};
    public static final byte[] UNDERLINE_OFF = {0x1B, 0x2D, 0x00};

    // Cut paper (partial cut)
    public static final byte[] PARTIAL_CUT = {0x1D, 0x56, 0x42, 0x00}; // GS V B 0

    // Feed n lines
    public static byte[] feed(int lines) {
        return new byte[]{0x1B, 0x64, (byte) lines}; // ESC d n
    }

    /**
     * GS v 0 – Raster bit-image header.
     * widthBytes = (widthInPixels + 7) / 8
     */
    public static byte[] rasterHeader(int widthBytes, int height) {
        return new byte[]{
                0x1D, 0x76, 0x30, 0x00,                     // GS v 0 m
                (byte) (widthBytes % 256),
                (byte) (widthBytes / 256),
                (byte) (height % 256),
                (byte) (height / 256)
        };
    }
}