package android.text;

public class SpannableStringBuilder implements Editable {
    private final StringBuilder sb;
    Runnable onChange;

    public SpannableStringBuilder() { sb = new StringBuilder(); }
    public SpannableStringBuilder(CharSequence s) { sb = new StringBuilder(s == null ? "" : s); }

    private Editable changed() { if (onChange != null) onChange.run(); return this; }

    @Override public Editable replace(int st, int en, CharSequence t) { sb.replace(st, en, t.toString()); return changed(); }
    @Override public Editable insert(int where, CharSequence t) { sb.insert(where, t); return changed(); }
    @Override public Editable delete(int st, int en) { sb.delete(st, en); return changed(); }
    @Override public Editable append(CharSequence t) { sb.append(t); return changed(); }
    @Override public Editable append(CharSequence t, int s, int e) { sb.append(t, s, e); return changed(); }
    @Override public Editable append(char c) { sb.append(c); return changed(); }
    @Override public void clear() { sb.setLength(0); changed(); }
    @Override public int length() { return sb.length(); }
    @Override public char charAt(int i) { return sb.charAt(i); }
    @Override public CharSequence subSequence(int s, int e) { return sb.subSequence(s, e); }
    @Override public String toString() { return sb.toString(); }
}
