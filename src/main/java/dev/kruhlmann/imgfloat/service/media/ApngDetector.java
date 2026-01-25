package dev.kruhlmann.imgfloat.service.media;

final class ApngDetector {

    private static final byte[] PNG_SIGNATURE = new byte[] {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    };

    private ApngDetector() {}

    static boolean isApng(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_SIGNATURE.length + 8) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        int offset = PNG_SIGNATURE.length;
        while (offset + 8 <= bytes.length) {
            int length = readInt(bytes, offset);
            if (length < 0) {
                return false;
            }
            int typeOffset = offset + 4;
            if (typeOffset + 4 > bytes.length) {
                return false;
            }
            if (
                bytes[typeOffset] == 'a' &&
                bytes[typeOffset + 1] == 'c' &&
                bytes[typeOffset + 2] == 'T' &&
                bytes[typeOffset + 3] == 'L'
            ) {
                return true;
            }
            long next = (long) offset + 12 + length;
            if (next > bytes.length) {
                return false;
            }
            offset = (int) next;
        }
        return false;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (
            ((bytes[offset] & 0xFF) << 24) |
            ((bytes[offset + 1] & 0xFF) << 16) |
            ((bytes[offset + 2] & 0xFF) << 8) |
            (bytes[offset + 3] & 0xFF)
        );
    }
}
