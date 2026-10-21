package com.apkrunner.res;

import java.util.LinkedHashMap;
import java.util.Map;

/** A resolved resource value (Res_value), or a bag (style/array/plurals). */
public final class Value {
    public static final int TYPE_NULL = 0, TYPE_REFERENCE = 1, TYPE_ATTRIBUTE = 2, TYPE_STRING = 3, TYPE_FLOAT = 4,
            TYPE_DIMENSION = 5, TYPE_FRACTION = 6, TYPE_INT_DEC = 0x10, TYPE_INT_HEX = 0x11, TYPE_INT_BOOLEAN = 0x12,
            TYPE_FIRST_COLOR = 0x1c, TYPE_LAST_COLOR = 0x1f;

    public final int type;
    public final int data;
    public final String string;
    /** Screen density of the config this value came from (0 = any). */
    public int density;
    public int parent;
    public Map<Integer, Value> bag;

    public Value(int type, int data, String string) { this.type = type; this.data = data; this.string = string; }

    public static Value bag(int parent) {
        Value v = new Value(TYPE_NULL, 0, null);
        v.parent = parent;
        v.bag = new LinkedHashMap<>();
        return v;
    }

    public boolean isColor() { return type >= TYPE_FIRST_COLOR && type <= TYPE_LAST_COLOR; }
    public boolean isInt() { return type >= TYPE_INT_DEC && type <= TYPE_LAST_COLOR; }

    private static final float[] RADIX = {1f / (1 << 8), 1f / (1 << 15), 1f / (1 << 23), 1f / (1L << 31)};

    /** Complex dimension -> px at mdpi (density 1). */
    public static float complexToPx(int data) {
        float v = (data & 0xFFFFFF00) * RADIX[(data >> 4) & 3];
        switch (data & 0xF) {
            case 3: return v * 160f / 72f;   // pt
            case 4: return v * 160f;         // in
            case 5: return v * 160f / 25.4f; // mm
            default: return v;               // px, dp, sp
        }
    }

    public static float complexToFraction(int data, float base) {
        return (data & 0xFFFFFF00) * RADIX[(data >> 4) & 3] * base;
    }

    public String coerceToString() {
        switch (type) {
            case TYPE_STRING: return string;
            case TYPE_INT_BOOLEAN: return data != 0 ? "true" : "false";
            case TYPE_FLOAT: return Float.toString(Float.intBitsToFloat(data));
            case TYPE_DIMENSION: return complexToPx(data) + "px";
            default:
                if (isColor()) return String.format("#%08x", data);
                return Integer.toString(data);
        }
    }
}
