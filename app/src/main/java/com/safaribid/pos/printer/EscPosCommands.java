package com.safaribid.pos.printer;

public final class EscPosCommands {

    private EscPosCommands() {
    }

    public static final byte[] INIT = {0x1B, 0x40};

    public static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};
    public static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};
    public static final byte[] ALIGN_RIGHT = {0x1B, 0x61, 0x02};

    public static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};
    public static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};

    public static final byte[] PARTIAL_CUT = {0x1D, 0x56, 0x42, 0x00};

    public static byte[] feed(int lines) {
        return new byte[]{0x1B, 0x64, (byte) lines};
    }

    public static byte[] rasterHeader(int widthBytes, int height) {
        return new byte[]{
                0x1D, 0x76, 0x30, 0x00,
                (byte) (widthBytes % 256),
                (byte) (widthBytes / 256),
                (byte) (height % 256),
                (byte) (height / 256)
        };
    }
}
