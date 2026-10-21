package android.os;

import java.util.ArrayList;

/** In-memory parcel: values are read back in the order they were written. */
public final class Parcel {
    private final ArrayList<Object> values = new ArrayList<>();
    private int pos;

    public static Parcel obtain() { return new Parcel(); }
    public void recycle() {}
    public int dataPosition() { return pos; }
    public void setDataPosition(int p) { pos = p; }

    private Object next() { return pos < values.size() ? values.get(pos++) : null; }

    public void writeInt(int v) { values.add(v); }
    public void writeLong(long v) { values.add(v); }
    public void writeFloat(float v) { values.add(v); }
    public void writeDouble(double v) { values.add(v); }
    public void writeString(String v) { values.add(v); }
    public void writeByte(byte v) { values.add(v); }
    public void writeValue(Object v) { values.add(v); }
    public void writeParcelable(Parcelable p, int flags) { values.add(p); }
    public void writeBundle(Bundle b) { values.add(b); }
    public int readInt() { Object o = next(); return o == null ? 0 : (Integer) o; }
    public long readLong() { Object o = next(); return o == null ? 0 : (Long) o; }
    public float readFloat() { Object o = next(); return o == null ? 0 : (Float) o; }
    public double readDouble() { Object o = next(); return o == null ? 0 : (Double) o; }
    public String readString() { return (String) next(); }
    public byte readByte() { Object o = next(); return o == null ? 0 : (Byte) o; }
    public Object readValue(ClassLoader l) { return next(); }
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> T readParcelable(ClassLoader l) { return (T) next(); }
    public Bundle readBundle() { return (Bundle) next(); }
    public Bundle readBundle(ClassLoader l) { return (Bundle) next(); }
}
