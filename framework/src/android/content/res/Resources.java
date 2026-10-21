package android.content.res;

import android.util.DisplayMetrics;

public class Resources {
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    private final DisplayMetrics metrics = new DisplayMetrics();

    public DisplayMetrics getDisplayMetrics() { return metrics; }

    public String getString(int id) {
        throw new NotFoundException("resources.arsc is not supported yet (id 0x" + Integer.toHexString(id) + ")");
    }
}
