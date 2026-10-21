package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import com.apkrunner.host.Host;

public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8, NO_ID = -1;

    public interface OnClickListener { void onClick(View v); }
    public interface OnLongClickListener { boolean onLongClick(View v); }
    public interface OnTouchListener { boolean onTouch(View v, MotionEvent e); }

    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0, EXACTLY = 1 << 30, AT_MOST = 2 << 30;
        private static final int MODE_MASK = 3 << 30;

        public static int makeMeasureSpec(int size, int mode) { return (size & ~MODE_MASK) | mode; }
        public static int getMode(int spec) { return spec & MODE_MASK; }
        public static int getSize(int spec) { return spec & ~MODE_MASK; }
    }

    private final Context context;
    private int id = NO_ID;
    private Object tag;
    private int visibility = VISIBLE;
    private int backgroundColor;
    private boolean enabled = true;
    protected int paddingLeft, paddingTop, paddingRight, paddingBottom;
    private int left, top, right, bottom;
    private int measuredWidth, measuredHeight;
    private ViewGroup.LayoutParams layoutParams;
    ViewGroup parent;
    private OnClickListener clickListener;
    private OnLongClickListener longClickListener;
    private OnTouchListener touchListener;
    private boolean pressed;

    public View(Context context) { this.context = context; }

    public Context getContext() { return context; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public Object getTag() { return tag; }
    public void setTag(Object t) { tag = t; }
    public ViewParent getParent() { return parent; }

    public View findViewById(int id) { return this.id == id ? this : null; }

    public int getVisibility() { return visibility; }
    public void setVisibility(int v) { visibility = v; requestLayout(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { enabled = e; invalidate(); }
    public boolean isPressed() { return pressed; }

    public void setBackgroundColor(int color) { backgroundColor = color; invalidate(); }
    public int getBackgroundColor() { return backgroundColor; }

    public void setPadding(int l, int t, int r, int b) { paddingLeft = l; paddingTop = t; paddingRight = r; paddingBottom = b; requestLayout(); }
    public int getPaddingLeft() { return paddingLeft; }
    public int getPaddingTop() { return paddingTop; }
    public int getPaddingRight() { return paddingRight; }
    public int getPaddingBottom() { return paddingBottom; }

    public ViewGroup.LayoutParams getLayoutParams() { return layoutParams; }
    public void setLayoutParams(ViewGroup.LayoutParams p) { layoutParams = p; requestLayout(); }

    public void setOnClickListener(OnClickListener l) { clickListener = l; }
    public void setOnLongClickListener(OnLongClickListener l) { longClickListener = l; }
    public void setOnTouchListener(OnTouchListener l) { touchListener = l; }
    public boolean performClick() {
        if (clickListener == null) return false;
        clickListener.onClick(this);
        return true;
    }
    public boolean hasOnClickListeners() { return clickListener != null; }

    // ---- measure / layout -------------------------------------------------

    public final void measure(int widthSpec, int heightSpec) { onMeasure(widthSpec, heightSpec); }

    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(getDefaultSize(paddingLeft + paddingRight, widthSpec),
                getDefaultSize(paddingTop + paddingBottom, heightSpec));
    }

    public static int getDefaultSize(int size, int spec) {
        switch (MeasureSpec.getMode(spec)) {
            case MeasureSpec.EXACTLY: return MeasureSpec.getSize(spec);
            case MeasureSpec.AT_MOST: return Math.min(size, MeasureSpec.getSize(spec));
            default: return size;
        }
    }

    public static int resolveSize(int size, int spec) { return getDefaultSize(size, spec); }

    protected final void setMeasuredDimension(int w, int h) { measuredWidth = w; measuredHeight = h; }
    public final int getMeasuredWidth() { return measuredWidth; }
    public final int getMeasuredHeight() { return measuredHeight; }

    public final void layout(int l, int t, int r, int b) {
        boolean changed = l != left || t != top || r != right || b != bottom;
        int ow = getWidth(), oh = getHeight();
        left = l; top = t; right = r; bottom = b;
        if (changed) onSizeChanged(getWidth(), getHeight(), ow, oh);
        onLayout(changed, l, t, r, b);
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {}
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {}

    public final int getLeft() { return left; }
    public final int getTop() { return top; }
    public final int getRight() { return right; }
    public final int getBottom() { return bottom; }
    public final int getWidth() { return right - left; }
    public final int getHeight() { return bottom - top; }

    public void requestLayout() { Host.requestLayout(); }
    public void invalidate() { Host.invalidate(); }
    public void postInvalidate() { Host.invalidate(); }
    public boolean post(Runnable r) { com.apkrunner.host.UiThread.post(r, 0); return true; }
    public boolean postDelayed(Runnable r, long ms) { com.apkrunner.host.UiThread.post(r, ms); return true; }

    // ---- drawing ------------------------------------------------------------

    public void draw(Canvas canvas) {
        if (backgroundColor != 0) {
            Paint p = new Paint();
            p.setColor(backgroundColor);
            canvas.drawRect(0, 0, getWidth(), getHeight(), p);
        }
        onDraw(canvas);
        dispatchDraw(canvas);
    }

    protected void onDraw(Canvas canvas) {}
    protected void dispatchDraw(Canvas canvas) {}

    // ---- touch ----------------------------------------------------------------

    public boolean dispatchTouchEvent(MotionEvent e) {
        if (touchListener != null && enabled && touchListener.onTouch(this, e)) return true;
        return onTouchEvent(e);
    }

    public boolean onTouchEvent(MotionEvent e) {
        if (!enabled || (clickListener == null && longClickListener == null)) return false;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: pressed = true; invalidate(); break;
            case MotionEvent.ACTION_UP:
                if (pressed) { pressed = false; invalidate(); performClick(); }
                break;
            case MotionEvent.ACTION_CANCEL: pressed = false; invalidate(); break;
        }
        return true;
    }
}
