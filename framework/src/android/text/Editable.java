package android.text;

public interface Editable extends CharSequence, Appendable {
    Editable replace(int st, int en, CharSequence text);
    Editable insert(int where, CharSequence text);
    Editable delete(int st, int en);
    @Override Editable append(CharSequence text);
    void clear();
}
