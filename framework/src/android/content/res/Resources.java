package android.content.res;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import com.apkrunner.host.Apk;
import com.apkrunner.res.Attrs;
import com.apkrunner.res.ResTable;
import com.apkrunner.res.Value;
import com.apkrunner.res.XmlDrawables;
import com.apkrunner.res.XmlNode;
import java.util.ArrayList;
import java.util.List;

public class Resources {
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    /** Themes are not applied yet; this exists so app code links and runs. */
    public final class Theme {
        public TypedArray obtainStyledAttributes(int[] attrs) { return new TypedArray(null, attrs); }
        public TypedArray obtainStyledAttributes(android.util.AttributeSet set, int[] attrs, int a, int b) {
            return new TypedArray(com.apkrunner.res.Attrs.of(set), attrs);
        }
        public boolean resolveAttribute(int resid, android.util.TypedValue out, boolean resolveRefs) { return false; }
        public void applyStyle(int resId, boolean force) {}
        public Resources getResources() { return Resources.this; }
    }

    private final Theme theme = new Theme();

    public Theme newTheme() { return theme; }
    public Theme theme() { return theme; }

    private final DisplayMetrics metrics = new DisplayMetrics();
    private ResTable table;
    private boolean loaded;

    public DisplayMetrics getDisplayMetrics() { return metrics; }

    /** The app's resource table, or null when the APK has none. */
    public ResTable table() {
        if (!loaded) {
            loaded = true;
            byte[] arsc = Apk.read("resources.arsc");
            if (arsc != null) table = new ResTable(arsc);
        }
        return table;
    }

    private Value value(int id) {
        Value v = table() == null ? null : table.resolve(table.get(id));
        if (v == null) throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id));
        return v;
    }

    public CharSequence getText(int id) { return value(id).coerceToString(); }
    public String getString(int id) { return value(id).coerceToString(); }
    public String getString(int id, Object... args) { return String.format(getString(id), args); }
    public String getQuantityString(int id, int quantity, Object... args) {
        Value v = value(id);
        String s = null;
        if (v.bag != null) {
            // plurals: 0x01000004 = other, 0x01000005 = zero, 0x01000006 = one
            Value pick = v.bag.get(quantity == 1 ? 0x01000006 : 0x01000004);
            if (pick == null) pick = v.bag.get(0x01000004);
            if (pick != null) s = table.resolve(pick).coerceToString();
        }
        if (s == null) s = v.coerceToString();
        return args.length == 0 ? s : String.format(s, args);
    }

    public String[] getStringArray(int id) {
        Value v = value(id);
        if (v.bag == null) return new String[0];
        List<String> out = new ArrayList<>();
        for (Value item : v.bag.values()) {
            Value r = table.resolve(item);
            out.add(r == null ? "" : r.coerceToString());
        }
        return out.toArray(new String[0]);
    }

    public int[] getIntArray(int id) {
        Value v = value(id);
        if (v.bag == null) return new int[0];
        int[] out = new int[v.bag.size()];
        int i = 0;
        for (Value item : v.bag.values()) out[i++] = table.resolve(item).data;
        return out;
    }

    public int getColor(int id) {
        Value v = value(id);
        if (v.type == Value.TYPE_STRING && v.string.endsWith(".xml")) return colorStateListDefault(v.string, 0);
        return v.data;
    }

    public int getColor(int id, Theme theme) { return getColor(id); }

    public float getDimension(int id) { return Attrs.dimension(value(id), 0); }
    public int getDimensionPixelSize(int id) { return Math.max(Math.round(getDimension(id)), getDimension(id) > 0 ? 1 : 0); }
    public int getDimensionPixelOffset(int id) { return (int) getDimension(id); }
    public int getInteger(int id) { return value(id).data; }
    public boolean getBoolean(int id) { return value(id).data != 0; }
    public float getFraction(int id, int base, int pbase) { return Value.complexToFraction(value(id).data, base); }

    public Drawable getDrawable(int id) {
        Value v = value(id);
        if (v.isColor()) return new ColorDrawable(v.data);
        if (v.type != Value.TYPE_STRING || v.string == null) throw new NotFoundException("Drawable #0x" + Integer.toHexString(id));
        return drawableFromFile(v.string, v.density);
    }

    public Drawable getDrawable(int id, Theme theme) { return getDrawable(id); }

    public Drawable drawableFromFile(String path, int density) {
        byte[] data = Apk.read(path);
        if (data == null) return new ColorDrawable(0);
        if (path.endsWith(".xml")) return XmlDrawables.inflate(XmlNode.parse(data), this);
        Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bm == null) return new ColorDrawable(0); // e.g. WebP, unsupported by ImageIO
        if (density > 0 && density < 0xFFFE && density != 160) bm = Bitmap.createScaledBitmap(bm,
                Math.max(1, bm.getWidth() * 160 / density), Math.max(1, bm.getHeight() * 160 / density), true);
        return new BitmapDrawable(this, bm);
    }

    /** The default color of a color state list XML (the last, stateless item). */
    public int colorStateListDefault(String path, int def) {
        byte[] data = Apk.read(path);
        if (data == null) return def;
        XmlNode sel = XmlNode.parse(data);
        int color = def;
        for (XmlNode item : sel.children) {
            boolean stateful = false;
            for (XmlNode.Attr a : item.attrs) if (a.name.startsWith("state_")) stateful = true;
            Attrs at = new Attrs(item, this);
            int c = at.color("color", def);
            if (at.has("alpha")) c = (Math.round(at.dimension("alpha", 1f) * 255) << 24) | (c & 0xFFFFFF);
            color = c;
            if (!stateful) break;
        }
        return color;
    }

    public XmlNode getLayoutNode(int id) {
        Value v = value(id);
        byte[] data = v.string == null ? null : Apk.read(v.string);
        if (data == null) throw new NotFoundException("Layout #0x" + Integer.toHexString(id));
        return XmlNode.parse(data);
    }

    public int getIdentifier(String name, String type, String pkg) {
        if (table() == null) return 0;
        int colon = name.indexOf(':'), slash = name.indexOf('/');
        if (slash > 0) { type = name.substring(colon + 1, slash); name = name.substring(slash + 1); }
        return table.identifier(type, name);
    }

    public String getResourceName(int id) {
        String n = table() == null ? null : table.name(id);
        if (n == null) throw new NotFoundException("#0x" + Integer.toHexString(id));
        return n;
    }

    public String getResourceEntryName(int id) { String n = getResourceName(id); return n.substring(n.indexOf('/') + 1); }
    public String getResourceTypeName(int id) { String n = getResourceName(id); return n.substring(n.indexOf(':') + 1, n.indexOf('/')); }
    public String getResourcePackageName(int id) { String n = getResourceName(id); return n.substring(0, n.indexOf(':')); }
}
