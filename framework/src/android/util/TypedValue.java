package android.util;

public class TypedValue {
    public static final int COMPLEX_UNIT_PX = 0, COMPLEX_UNIT_DIP = 1, COMPLEX_UNIT_SP = 2;

    public static float applyDimension(int unit, float value, DisplayMetrics m) {
        switch (unit) {
            case COMPLEX_UNIT_DIP: return value * m.density;
            case COMPLEX_UNIT_SP: return value * m.scaledDensity;
            default: return value;
        }
    }
}
