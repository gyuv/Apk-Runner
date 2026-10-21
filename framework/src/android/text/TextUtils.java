package android.text;

import java.util.Arrays;

public class TextUtils {
    public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; }
    public static boolean equals(CharSequence a, CharSequence b) { return a == b || (a != null && b != null && a.toString().equals(b.toString())); }
    public static String join(CharSequence sep, Iterable<?> items) {
        StringBuilder sb = new StringBuilder();
        for (Object o : items) { if (sb.length() > 0) sb.append(sep); sb.append(o); }
        return sb.toString();
    }
    public static String join(CharSequence sep, Object[] items) { return join(sep, Arrays.asList(items)); }
    public static String[] split(String text, String expression) { return text.isEmpty() ? new String[0] : text.split(expression, -1); }
    public static boolean isDigitsOnly(CharSequence s) { for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false; return true; }
    public static int getTrimmedLength(CharSequence s) { return s.toString().trim().length(); }
}
