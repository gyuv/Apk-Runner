package android.os;

public final class SystemClock {
    private static final long START = System.nanoTime();

    public static long uptimeMillis() { return (System.nanoTime() - START) / 1_000_000; }
    public static long elapsedRealtime() { return uptimeMillis(); }
    public static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
}
