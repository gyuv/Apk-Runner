package android.content.res;

import android.graphics.drawable.Drawable;
import com.apkrunner.res.Attrs;
import com.apkrunner.res.Value;
import com.apkrunner.res.XmlNode;

/** obtainStyledAttributes result: looks attributes up by resource ID. Themes/styles are not applied yet. */
public class TypedArray {
    private final Attrs attrs;
    private final int[] ids;

    public TypedArray(Attrs attrs, int[] ids) { this.attrs = attrs; this.ids = ids == null ? new int[0] : ids; }

    private XmlNode.Attr attr(int index) {
        return attrs == null || index < 0 || index >= ids.length ? null : attrs.node.attrById(ids[index]);
    }

    private Value value(int index) { return attrs == null ? null : attrs.value(attr(index)); }

    public int length() { return ids.length; }
    public int getIndexCount() { int n = 0; for (int i = 0; i < ids.length; i++) if (attr(i) != null) n++; return n; }
    public int getIndex(int at) { for (int i = 0; i < ids.length; i++) if (attr(i) != null && at-- == 0) return i; return -1; }
    public boolean hasValue(int index) { return attr(index) != null; }

    public String getString(int index) { Value v = value(index); return v == null ? null : v.coerceToString(); }
    public CharSequence getText(int index) { return getString(index); }
    public boolean getBoolean(int index, boolean def) { Value v = value(index); return v == null ? def : v.data != 0; }
    public int getInt(int index, int def) { Value v = value(index); return v == null || !v.isInt() ? def : v.data; }
    public int getInteger(int index, int def) { return getInt(index, def); }
    public float getFloat(int index, float def) {
        Value v = value(index);
        if (v == null) return def;
        return v.type == Value.TYPE_FLOAT ? Float.intBitsToFloat(v.data) : v.isInt() ? v.data : def;
    }
    public int getColor(int index, int def) { return attrs == null ? def : attrs.color(value(index), def); }
    public float getDimension(int index, float def) { return Attrs.dimension(value(index), def); }
    public int getDimensionPixelSize(int index, int def) { return Math.round(getDimension(index, def)); }
    public int getDimensionPixelOffset(int index, int def) { return (int) getDimension(index, def); }
    public int getLayoutDimension(int index, int def) { return (int) getDimension(index, def); }
    public int getLayoutDimension(int index, String name) { return (int) getDimension(index, 0); }
    public int getResourceId(int index, int def) {
        XmlNode.Attr a = attr(index);
        return a != null && a.type == Value.TYPE_REFERENCE ? a.data : def;
    }
    public Drawable getDrawable(int index) { return attrs == null ? null : attrs.drawable(attr(index)); }
    public void recycle() {}
}
