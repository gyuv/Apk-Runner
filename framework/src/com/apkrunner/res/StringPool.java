package com.apkrunner.res;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** ResStringPool chunk reader (UTF-8 and UTF-16). */
public final class StringPool {
    private final ByteBuffer buf;
    private final int[] offsets;
    private final int dataStart;
    private final boolean utf8;
    private final String[] cache;

    public StringPool(ByteBuffer src, int base) {
        buf = src.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int headerSize = buf.getShort(base + 2) & 0xFFFF;
        int count = buf.getInt(base + 8);
        int flags = buf.getInt(base + 16);
        dataStart = base + buf.getInt(base + 20);
        utf8 = (flags & (1 << 8)) != 0;
        offsets = new int[count];
        for (int i = 0; i < count; i++) offsets[i] = buf.getInt(base + headerSize + 4 * i);
        cache = new String[count];
    }

    public int size() { return offsets.length; }

    public String get(int i) {
        if (i < 0 || i >= offsets.length) return null;
        String s = cache[i];
        if (s != null) return s;
        int p = dataStart + offsets[i];
        if (utf8) {
            p += (buf.get(p) & 0x80) != 0 ? 2 : 1; // utf-16 length, unused
            int n = buf.get(p) & 0xFF;
            if ((n & 0x80) != 0) { n = ((n & 0x7F) << 8) | (buf.get(p + 1) & 0xFF); p += 2; } else p += 1;
            byte[] b = new byte[n];
            buf.position(p);
            buf.get(b);
            s = new String(b, StandardCharsets.UTF_8);
        } else {
            int n = buf.getShort(p) & 0xFFFF;
            if ((n & 0x8000) != 0) { n = ((n & 0x7FFF) << 16) | (buf.getShort(p + 2) & 0xFFFF); p += 4; } else p += 2;
            char[] c = new char[n];
            for (int k = 0; k < n; k++) c[k] = buf.getChar(p + 2 * k);
            s = new String(c);
        }
        return cache[i] = s;
    }
}
