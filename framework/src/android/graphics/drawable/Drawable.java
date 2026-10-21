package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

public abstract class Drawable {
    private final Rect bounds = new Rect();
    private int[] state = new int[0];
    private int level;

    public abstract void draw(Canvas canvas);

    public void setBounds(int l, int t, int r, int b) { bounds.set(l, t, r, b); onBoundsChange(bounds); }
    public void setBounds(Rect r) { setBounds(r.left, r.top, r.right, r.bottom); }
    public final Rect getBounds() { return bounds; }
    protected void onBoundsChange(Rect bounds) {}
    public int getIntrinsicWidth() { return -1; }
    public int getIntrinsicHeight() { return -1; }
    public int getMinimumWidth() { return Math.max(0, getIntrinsicWidth()); }
    public int getMinimumHeight() { return Math.max(0, getIntrinsicHeight()); }
    public void setAlpha(int alpha) {}
    public int getAlpha() { return 255; }
    public void setColorFilter(Object filter) {}
    public void setColorFilter(int color, Object mode) {}
    public void clearColorFilter() {}
    public void setTint(int color) {}
    public void setTintList(Object list) {}
    public int getOpacity() { return -3; }
    public Drawable mutate() { return this; }
    public boolean setState(int[] s) { state = s; return false; }
    public int[] getState() { return state; }
    public boolean setLevel(int l) { level = l; return false; }
    public int getLevel() { return level; }
    public boolean isStateful() { return false; }
    public void invalidateSelf() { com.apkrunner.host.Host.invalidate(); }
    public boolean setVisible(boolean visible, boolean restart) { return false; }
    public Drawable getCurrent() { return this; }
    public boolean getPadding(Rect padding) { padding.set(0, 0, 0, 0); return false; }

    public interface Callback {
        void invalidateDrawable(Drawable who);
        void scheduleDrawable(Drawable who, Runnable what, long when);
        void unscheduleDrawable(Drawable who, Runnable what);
    }

    public void setCallback(Callback cb) {}
}
