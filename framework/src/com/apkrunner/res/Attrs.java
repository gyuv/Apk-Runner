package com.apkrunner.res;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

/** AttributeSet over a binary XML element, with typed accessors used by the framework views. */
public final class Attrs implements AttributeSet {
    public final XmlNode node;
    private final Resources res;

    public Attrs(XmlNode node, Resources res) { this.node = node; this.res = res; }

    public static Attrs of(AttributeSet set) { return set instanceof Attrs ? (Attrs) set : null; }

    /** Typed value of an attribute, references resolved. Null when absent. */
    public Value value(XmlNode.Attr a) {
        if (a == null) return null;
        if (a.type == Value.TYPE_STRING || (a.type == Value.TYPE_NULL && a.raw != null))
            return new Value(Value.TYPE_STRING, 0, a.raw);
        if (a.type == Value.TYPE_ATTRIBUTE) return null; // theme attribute: themes not supported yet
        Value v = new Value(a.type, a.data, null);
        return v.type == Value.TYPE_REFERENCE ? res.table() == null ? null : res.table().resolve(v) : v;
    }

    public Value value(String name) { return value(node.attr(name)); }
    public boolean has(String name) { return node.attr(name) != null; }

    public int resourceId(String name) {
        XmlNode.Attr a = node.attr(name);
        return a != null && a.type == Value.TYPE_REFERENCE ? a.data : 0;
    }

    public CharSequence text(String name) {
        Value v = value(name);
        return v == null ? null : v.coerceToString();
    }

    public float dimension(String name, float def) { return dimension(value(name), def); }

    public static float dimension(Value v, float def) {
        if (v == null) return def;
        switch (v.type) {
            case Value.TYPE_DIMENSION: return Value.complexToPx(v.data);
            case Value.TYPE_FLOAT: return Float.intBitsToFloat(v.data);
            case Value.TYPE_STRING: return parseDimension(v.string, def);
            default: return v.isInt() ? v.data : def;
        }
    }

    private static float parseDimension(String s, float def) {
        try {
            if (s.equals("match_parent") || s.equals("fill_parent")) return -1;
            if (s.equals("wrap_content")) return -2;
            return Float.parseFloat(s.replaceAll("[a-z]+$", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int color(String name, int def) { return color(value(name), def); }

    public int color(Value v, int def) {
        if (v == null) return def;
        if (v.isColor() || v.isInt()) return v.data;
        if (v.type == Value.TYPE_STRING && v.string != null) {
            if (v.string.startsWith("#")) return Color.parseColor(v.string);
            if (v.string.endsWith(".xml")) return res.colorStateListDefault(v.string, def);
        }
        return def;
    }

    public int intValue(String name, int def) {
        Value v = value(name);
        if (v == null) return def;
        if (v.isInt()) return v.data;
        if (v.type == Value.TYPE_STRING) try { return Integer.decode(v.string); } catch (NumberFormatException ignored) {}
        return def;
    }

    public boolean bool(String name, boolean def) {
        Value v = value(name);
        if (v == null) return def;
        if (v.type == Value.TYPE_STRING) return Boolean.parseBoolean(v.string);
        return v.data != 0;
    }

    public Drawable drawable(String name) { return drawable(node.attr(name)); }

    public Drawable drawable(XmlNode.Attr a) {
        if (a == null) return null;
        if (a.type == Value.TYPE_REFERENCE) return a.data == 0 ? null : res.getDrawable(a.data);
        Value v = value(a);
        if (v == null) return null;
        if (v.isColor()) return new ColorDrawable(v.data);
        if (v.type == Value.TYPE_STRING && v.string != null && v.string.startsWith("#")) return new ColorDrawable(Color.parseColor(v.string));
        return null;
    }

    public Resources resources() { return res; }

    // ---- AttributeSet --------------------------------------------------------

    @Override public int getAttributeCount() { return node.attrs.size(); }
    @Override public String getAttributeName(int i) { return node.attrs.get(i).name; }
    @Override public int getAttributeNameResource(int i) { return node.attrs.get(i).resId; }

    @Override
    public String getAttributeValue(int i) {
        XmlNode.Attr a = node.attrs.get(i);
        if (a.raw != null) return a.raw;
        if (a.type == Value.TYPE_REFERENCE) return "@" + a.data;
        return new Value(a.type, a.data, null).coerceToString();
    }

    private XmlNode.Attr find(String ns, String name) {
        for (XmlNode.Attr a : node.attrs)
            if (a.name.equals(name) && (ns == null || ns.equals(a.ns) || (a.ns == null && ns.isEmpty()))) return a;
        return null;
    }

    @Override
    public String getAttributeValue(String ns, String name) {
        XmlNode.Attr a = find(ns, name);
        return a == null ? null : getAttributeValue(node.attrs.indexOf(a));
    }

    @Override
    public int getAttributeResourceValue(String ns, String name, int def) {
        XmlNode.Attr a = find(ns, name);
        return a != null && a.type == Value.TYPE_REFERENCE ? a.data : def;
    }

    @Override
    public int getAttributeIntValue(String ns, String name, int def) {
        XmlNode.Attr a = find(ns, name);
        return a != null && a.type >= Value.TYPE_INT_DEC ? a.data : def;
    }

    @Override
    public boolean getAttributeBooleanValue(String ns, String name, boolean def) {
        XmlNode.Attr a = find(ns, name);
        return a != null && a.type == Value.TYPE_INT_BOOLEAN ? a.data != 0 : def;
    }

    @Override
    public float getAttributeFloatValue(String ns, String name, float def) {
        XmlNode.Attr a = find(ns, name);
        return a != null && a.type == Value.TYPE_FLOAT ? Float.intBitsToFloat(a.data) : def;
    }

    @Override public String getIdAttribute() { return getAttributeValue(null, "id"); }
    @Override public String getClassAttribute() { return getAttributeValue(null, "class"); }
    @Override public int getIdAttributeResourceValue(int def) { return resourceId("id") != 0 ? resourceId("id") : def; }
    @Override public String getPositionDescription() { return "<" + node.name + ">"; }
}
