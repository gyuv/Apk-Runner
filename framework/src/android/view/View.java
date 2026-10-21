package android.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import com.apkrunner.host.Host;
import com.apkrunner.res.Attrs;

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
    private Drawable background;
    private int minWidth, minHeight;
    private float alpha = 1f;
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

    public View(Context context, AttributeSet set) { this(context, set, 0); }
    public View(Context context, AttributeSet set, int defStyleAttr) { this(context, set, defStyleAttr, 0); }

    public View(Context context, AttributeSet set, int defStyleAttr, int defStyleRes) {
        this(context);
        Attrs a = Attrs.of(set);
        if (a == null) return;
        if (a.resourceId("id") != 0) id = a.resourceId("id");
        float p = a.dimension("padding", -1);
        if (p >= 0) paddingLeft = paddingTop = paddingRight = paddingBottom = (int) p;
        float ph = a.dimension("paddingHorizontal", -1), pv = a.dimension("paddingVertical", -1);
        if (ph >= 0) paddingLeft = paddingRight = (int) ph;
        if (pv >= 0) paddingTop = paddingBottom = (int) pv;
        paddingLeft = (int) a.dimension("paddingLeft", a.dimension("paddingStart", paddingLeft));
        paddingRight = (int) a.dimension("paddingRight", a.dimension("paddingEnd", paddingRight));
        paddingTop = (int) a.dimension("paddingTop", paddingTop);
        paddingBottom = (int) a.dimension("paddingBottom", paddingBottom);
        Drawable bg = a.drawable("background");
        if (bg != null) background = bg;
        int vis = a.intValue("visibility", 0);
        visibility = vis == 1 ? INVISIBLE : vis == 2 ? GONE : VISIBLE;
        enabled = a.bool("enabled", true);
        minWidth = (int) a.dimension("minWidth", 0);
        minHeight = (int) a.dimension("minHeight", 0);
        alpha = a.dimension("alpha", 1f);
        final CharSequence onClick = a.text("onClick");
        if (onClick != null) {
            clickListener = v -> {
                try {
                    context.getClass().getMethod(onClick.toString(), View.class).invoke(context, v);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("android:onClick method " + onClick + " not found", e);
                }
            };
        }
    }

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

    public void setBackgroundColor(int color) { setBackground(new ColorDrawable(color)); }
    public int getBackgroundColor() { return background instanceof ColorDrawable ? ((ColorDrawable) background).getColor() : 0; }
    public void setBackground(Drawable d) { background = d; invalidate(); }
    @Deprecated public void setBackgroundDrawable(Drawable d) { setBackground(d); }
    public Drawable getBackground() { return background; }
    public void setBackgroundResource(int id) { setBackground(id == 0 ? null : context.getResources().getDrawable(id)); }
    public Resources getResources() { return context.getResources(); }
    public void setMinimumWidth(int w) { minWidth = w; requestLayout(); }
    public void setMinimumHeight(int h) { minHeight = h; requestLayout(); }
    public int getMinimumWidth() { return minWidth; }
    public int getMinimumHeight() { return minHeight; }
    protected int getSuggestedMinimumWidth() {
        return Math.max(minWidth, background == null ? 0 : background.getMinimumWidth());
    }
    protected int getSuggestedMinimumHeight() {
        return Math.max(minHeight, background == null ? 0 : background.getMinimumHeight());
    }
    public void setAlpha(float a) { alpha = a; invalidate(); }
    public float getAlpha() { return alpha; }
    public static View inflate(Context ctx, int layout, ViewGroup root) {
        return LayoutInflater.from(ctx).inflate(layout, root);
    }

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
        setMeasuredDimension(getDefaultSize(Math.max(getSuggestedMinimumWidth(), paddingLeft + paddingRight), widthSpec),
                getDefaultSize(Math.max(getSuggestedMinimumHeight(), paddingTop + paddingBottom), heightSpec));
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

    private static final ViewTreeObserver OBSERVER = new ViewTreeObserver();

    public ViewTreeObserver getViewTreeObserver() { return OBSERVER; }
    public View getRootView() { View v = this; while (v.parent != null) v = v.parent; return v; }
    public void requestLayout() { Host.requestLayout(); }
    public void invalidate() { Host.invalidate(); }
    public void postInvalidate() { Host.invalidate(); }
    public boolean post(Runnable r) { com.apkrunner.host.UiThread.post(r, 0); return true; }
    public boolean postDelayed(Runnable r, long ms) { com.apkrunner.host.UiThread.post(r, ms); return true; }

    // ---- drawing ------------------------------------------------------------

    public void draw(Canvas canvas) {
        if (background != null) {
            background.setBounds(0, 0, getWidth(), getHeight());
            background.draw(canvas);
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
