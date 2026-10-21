package com.apkrunner.res;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * resources.arsc reader. Picks, per resource, the value whose configuration best
 * matches a phone-sized, portrait, light-mode, mdpi, default-locale device.
 */
public final class ResTable {
    private static final class Candidate {
        final int score;
        final Value value;
        Candidate(int score, Value value) { this.score = score; this.value = value; }
    }

    private final Map<Integer, Candidate> best = new HashMap<>();
    private final Map<String, Integer> names = new HashMap<>();     // "type/name" -> id
    private final Map<Integer, String> idNames = new HashMap<>();   // id -> "pkg:type/name"
    private String packageName = "";

    public ResTable(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int headerSize = b.getShort(2) & 0xFFFF;
        StringPool global = null;
        int off = headerSize;
        while (off + 8 <= data.length) {
            int type = b.getShort(off) & 0xFFFF, size = b.getInt(off + 4);
            if (size <= 0) break;
            if (type == 0x0001) global = new StringPool(b, off);
            else if (type == 0x0200) readPackage(b, off, global);
            off += size;
        }
    }

    public String packageName() { return packageName; }

    private void readPackage(ByteBuffer b, int base, StringPool global) {
        int headerSize = b.getShort(base + 2) & 0xFFFF, size = b.getInt(base + 4);
        int pkgId = b.getInt(base + 8);
        StringBuilder nm = new StringBuilder();
        for (int i = 0; i < 128; i++) {
            char c = b.getChar(base + 12 + 2 * i);
            if (c == 0) break;
            nm.append(c);
        }
        if (packageName.isEmpty()) packageName = nm.toString();
        StringPool typeStrings = new StringPool(b, base + b.getInt(base + 268));
        StringPool keyStrings = new StringPool(b, base + b.getInt(base + 276));
        int off = base + headerSize;
        while (off < base + size) {
            int type = b.getShort(off) & 0xFFFF, csize = b.getInt(off + 4);
            if (csize <= 0) break;
            if (type == 0x0201) readType(b, off, pkgId, global, typeStrings, keyStrings, nm.toString());
            off += csize;
        }
    }

    private void readType(ByteBuffer b, int base, int pkgId, StringPool global, StringPool typeStrings,
                          StringPool keys, String pkg) {
        int headerSize = b.getShort(base + 2) & 0xFFFF;
        int typeId = b.get(base + 8) & 0xFF, flags = b.get(base + 9) & 0xFF;
        int count = b.getInt(base + 12), entriesStart = b.getInt(base + 16);
        int cfg = base + 20;
        int score = configScore(b, cfg);
        if (score < 0) return;
        int density = b.getShort(cfg + 14) & 0xFFFF;
        String typeName = typeStrings.get(typeId - 1);
        boolean sparse = (flags & 1) != 0, offset16 = (flags & 2) != 0;
        int idx = base + headerSize;
        for (int i = 0; i < count; i++) {
            int entry, eoff;
            if (sparse) {
                entry = b.getShort(idx + 4 * i) & 0xFFFF;
                eoff = (b.getShort(idx + 4 * i + 2) & 0xFFFF) * 4;
            } else if (offset16) {
                entry = i;
                int o = b.getShort(idx + 2 * i) & 0xFFFF;
                if (o == 0xFFFF) continue;
                eoff = o * 4;
            } else {
                entry = i;
                eoff = b.getInt(idx + 4 * i);
                if (eoff == -1) continue;
            }
            int p = base + entriesStart + eoff;
            int id = (pkgId << 24) | (typeId << 16) | entry;
            int eflags = b.getShort(p + 2) & 0xFFFF;
            String key;
            Value v;
            if ((eflags & 0x8) != 0) { // compact entry
                key = keys.get(b.getShort(p) & 0xFFFF);
                int dtype = eflags >>> 8, data = b.getInt(p + 4);
                v = new Value(dtype, data, dtype == Value.TYPE_STRING ? global.get(data) : null);
            } else {
                int esize = b.getShort(p) & 0xFFFF;
                key = keys.get(b.getInt(p + 4));
                if ((eflags & 1) != 0) { // complex (bag)
                    v = Value.bag(b.getInt(p + 8));
                    int n = b.getInt(p + 12), m = p + esize;
                    for (int k = 0; k < n; k++, m += 12) v.bag.put(b.getInt(m), readValue(b, m + 4, global));
                } else {
                    v = readValue(b, p + esize, global);
                }
            }
            v.density = density;
            String full = typeName + "/" + key;
            names.put(full, id);
            idNames.put(id, pkg + ":" + full);
            Candidate cur = best.get(id);
            if (cur == null || score > cur.score) best.put(id, new Candidate(score, v));
        }
    }

    private static Value readValue(ByteBuffer b, int p, StringPool global) {
        int type = b.get(p + 3) & 0xFF, data = b.getInt(p + 4);
        return new Value(type, data, type == Value.TYPE_STRING ? global.get(data) : null);
    }

    /** -1 when the config doesn't apply to our virtual device; higher is a better match. */
    private static int configScore(ByteBuffer b, int c) {
        int size = b.getInt(c);
        if (b.getInt(c + 4) != 0) return -1;                         // mcc/mnc
        if (b.getShort(c + 8) != 0) return -1;                       // language
        int orientation = b.get(c + 12) & 0xFF;
        if (orientation > 1) return -1;                              // land/square
        int density = b.getShort(c + 14) & 0xFFFF;
        int qualifiers = (orientation != 0 ? 1 : 0);
        int sdk = size >= 28 ? b.getShort(c + 24) & 0xFFFF : 0;
        if (sdk > 34) return -1;
        if (size >= 32) {
            int screenLayout = b.get(c + 28) & 0xFF, uiMode = b.get(c + 29) & 0xFF;
            int sw = b.getShort(c + 30) & 0xFFFF;
            if ((screenLayout & 0x0F) > 2) return -1;                // large/xlarge
            if ((screenLayout & 0x30) == 0x20) return -1;            // long screens: fine either way
            if ((uiMode & 0x30) == 0x20) return -1;                  // night
            if ((uiMode & 0x0F) > 1) return -1;                      // car/tv/watch...
            if (sw > 360) return -1;
            if (sw != 0) qualifiers++;
        }
        if (size >= 36) {
            int w = b.getShort(c + 32) & 0xFFFF, h = b.getShort(c + 34) & 0xFFFF;
            if (w > 360 || h > 640) return -1;
            if (w != 0 || h != 0) qualifiers++;
        }
        int dscore;
        if (density == 160) dscore = 100;
        else if (density == 0xFFFE) dscore = 98;                     // anydpi
        else if (density == 0xFFFF) dscore = 97;                     // nodpi
        else if (density == 0) dscore = 96;
        else if (density > 160) dscore = 90 - Math.min(80, (density - 160) / 10);
        else dscore = 5 + density / 20;
        return dscore * 100000 + sdk * 100 + qualifiers;
    }

    public Value get(int id) {
        Candidate c = best.get(id);
        return c == null ? null : c.value;
    }

    /** Follow references until a concrete value. */
    public Value resolve(Value v) {
        for (int i = 0; v != null && v.type == Value.TYPE_REFERENCE && i < 16; i++) {
            if (v.data == 0) return null;
            v = get(v.data);
        }
        return v;
    }

    public int identifier(String type, String name) {
        Integer id = names.get(type + "/" + name);
        return id == null ? 0 : id;
    }

    public String name(int id) { return idNames.get(id); }

    public List<Integer> ids() { return new ArrayList<>(best.keySet()); }
}
