package android.os;

import java.util.HashMap;
import java.util.Set;

public class Bundle {
    private final HashMap<String, Object> map = new HashMap<>();

    public Bundle() {}
    public Bundle(Bundle b) { if (b != null) map.putAll(b.map); }
    public boolean containsKey(String k) { return map.containsKey(k); }
    public Set<String> keySet() { return map.keySet(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public Object get(String k) { return map.get(k); }
    public void remove(String k) { map.remove(k); }
    public void putAll(Bundle b) { map.putAll(b.map); }
    public void putString(String k, String v) { map.put(k, v); }
    public void putInt(String k, int v) { map.put(k, v); }
    public void putLong(String k, long v) { map.put(k, v); }
    public void putBoolean(String k, boolean v) { map.put(k, v); }
    public void putFloat(String k, float v) { map.put(k, v); }
    public void putDouble(String k, double v) { map.put(k, v); }
    public String getString(String k) { return (String) map.get(k); }
    public String getString(String k, String d) { Object v = map.get(k); return v == null ? d : (String) v; }
    public int getInt(String k) { return getInt(k, 0); }
    public int getInt(String k, int d) { Object v = map.get(k); return v == null ? d : (Integer) v; }
    public long getLong(String k) { return getLong(k, 0L); }
    public long getLong(String k, long d) { Object v = map.get(k); return v == null ? d : (Long) v; }
    public boolean getBoolean(String k) { return getBoolean(k, false); }
    public boolean getBoolean(String k, boolean d) { Object v = map.get(k); return v == null ? d : (Boolean) v; }
    public float getFloat(String k) { return getFloat(k, 0f); }
    public float getFloat(String k, float d) { Object v = map.get(k); return v == null ? d : (Float) v; }
    public double getDouble(String k) { return getDouble(k, 0d); }
    public double getDouble(String k, double d) { Object v = map.get(k); return v == null ? d : (Double) v; }
}
