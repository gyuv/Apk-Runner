package android.util;

public final class Log {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6;

    private static int print(String level, String tag, String msg, Throwable tr) {
        System.err.println(level + "/" + tag + ": " + msg);
        if (tr != null) tr.printStackTrace();
        return 0;
    }

    public static int v(String t, String m) { return print("V", t, m, null); }
    public static int d(String t, String m) { return print("D", t, m, null); }
    public static int i(String t, String m) { return print("I", t, m, null); }
    public static int w(String t, String m) { return print("W", t, m, null); }
    public static int w(String t, String m, Throwable tr) { return print("W", t, m, tr); }
    public static int e(String t, String m) { return print("E", t, m, null); }
    public static int e(String t, String m, Throwable tr) { return print("E", t, m, tr); }
    public static boolean isLoggable(String t, int level) { return level >= INFO; }
}
