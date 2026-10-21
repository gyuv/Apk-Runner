package android.view;

import java.util.ArrayList;
import java.util.List;

/** Global layout/draw callbacks, fired by the host after each layout pass. */
public final class ViewTreeObserver {
    public interface OnGlobalLayoutListener { void onGlobalLayout(); }
    public interface OnPreDrawListener { boolean onPreDraw(); }
    public interface OnScrollChangedListener { void onScrollChanged(); }
    public interface OnGlobalFocusChangeListener { void onGlobalFocusChanged(View oldFocus, View newFocus); }
    public interface OnTouchModeChangeListener { void onTouchModeChanged(boolean isInTouchMode); }
    public interface OnDrawListener { void onDraw(); }

    private static final List<OnGlobalLayoutListener> LAYOUT = new ArrayList<>();
    private static final List<OnPreDrawListener> PRE_DRAW = new ArrayList<>();

    public boolean isAlive() { return true; }
    public void addOnGlobalLayoutListener(OnGlobalLayoutListener l) { LAYOUT.add(l); }
    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l) { LAYOUT.remove(l); }
    @Deprecated public void removeGlobalOnLayoutListener(OnGlobalLayoutListener l) { LAYOUT.remove(l); }
    public void addOnPreDrawListener(OnPreDrawListener l) { PRE_DRAW.add(l); }
    public void removeOnPreDrawListener(OnPreDrawListener l) { PRE_DRAW.remove(l); }
    public void addOnScrollChangedListener(OnScrollChangedListener l) {}
    public void removeOnScrollChangedListener(OnScrollChangedListener l) {}
    public void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) {}
    public void removeOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) {}
    public void addOnTouchModeChangeListener(OnTouchModeChangeListener l) {}
    public void removeOnTouchModeChangeListener(OnTouchModeChangeListener l) {}
    public void addOnDrawListener(OnDrawListener l) {}
    public void removeOnDrawListener(OnDrawListener l) {}

    public static void dispatchGlobalLayout() {
        for (OnGlobalLayoutListener l : new ArrayList<>(LAYOUT)) l.onGlobalLayout();
    }

    public static void dispatchPreDraw() {
        for (OnPreDrawListener l : new ArrayList<>(PRE_DRAW)) l.onPreDraw();
    }
}
