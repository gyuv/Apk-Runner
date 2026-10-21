package android.view;

import android.content.Context;
import android.graphics.Canvas;
import java.util.ArrayList;

public abstract class ViewGroup extends View implements ViewParent {
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1, FILL_PARENT = -1, WRAP_CONTENT = -2;
        public int width, height;

        public LayoutParams(int w, int h) { width = w; height = h; }
        public LayoutParams(LayoutParams src) { this(src.width, src.height); }
    }

    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;

        public MarginLayoutParams(int w, int h) { super(w, h); }
        public MarginLayoutParams(LayoutParams src) { super(src); }
        public void setMargins(int l, int t, int r, int b) { leftMargin = l; topMargin = t; rightMargin = r; bottomMargin = b; }
    }

    private final ArrayList<View> children = new ArrayList<>();
    private View touchTarget;

    public ViewGroup(Context c) { super(c); }

    public void addView(View child) { addView(child, -1); }
    public void addView(View child, int index) {
        if (child.getLayoutParams() == null) child.setLayoutParams(generateDefaultLayoutParams());
        child.parent = this;
        if (index < 0) children.add(child); else children.add(index, child);
        requestLayout();
    }
    public void addView(View child, LayoutParams p) { child.setLayoutParams(p); addView(child, -1); }
    public void addView(View child, int w, int h) { addView(child, generateLayoutParams(new LayoutParams(w, h))); }
    public void removeView(View child) { children.remove(child); child.parent = null; requestLayout(); }
    public void removeAllViews() { for (View c : children) c.parent = null; children.clear(); requestLayout(); }
    public int getChildCount() { return children.size(); }
    public View getChildAt(int i) { return children.get(i); }

    protected LayoutParams generateDefaultLayoutParams() { return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
    protected LayoutParams generateLayoutParams(LayoutParams p) { return p instanceof MarginLayoutParams ? p : new MarginLayoutParams(p); }

    @Override
    public View findViewById(int id) {
        if (getId() == id) return this;
        for (View c : children) {
            View v = c.findViewById(id);
            if (v != null) return v;
        }
        return null;
    }

    /** Child measure spec from the parent spec, as Android does it. */
    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        int size = Math.max(0, MeasureSpec.getSize(spec) - padding);
        int mode = MeasureSpec.getMode(spec);
        if (childDimension >= 0) return MeasureSpec.makeMeasureSpec(childDimension, MeasureSpec.EXACTLY);
        if (childDimension == LayoutParams.MATCH_PARENT)
            return MeasureSpec.makeMeasureSpec(size, mode == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED : MeasureSpec.EXACTLY);
        return MeasureSpec.makeMeasureSpec(size, mode == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST);
    }

    protected void measureChildWithMargins(View child, int wSpec, int usedW, int hSpec, int usedH) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        child.measure(
                getChildMeasureSpec(wSpec, paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin + usedW, lp.width),
                getChildMeasureSpec(hSpec, paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin + usedH, lp.height));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (View c : children) {
            if (c.getVisibility() != VISIBLE) continue;
            canvas.save();
            canvas.translate(c.getLeft(), c.getTop());
            canvas.clipRect(0, 0, c.getWidth(), c.getHeight());
            c.draw(canvas);
            canvas.restore();
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent e) { return false; }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            touchTarget = null;
            if (!onInterceptTouchEvent(e)) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    View c = children.get(i);
                    if (c.getVisibility() != VISIBLE) continue;
                    if (e.getX() >= c.getLeft() && e.getX() < c.getRight() && e.getY() >= c.getTop() && e.getY() < c.getBottom()) {
                        e.offsetLocation(-c.getLeft(), -c.getTop());
                        boolean handled = c.dispatchTouchEvent(e);
                        e.offsetLocation(c.getLeft(), c.getTop());
                        if (handled) { touchTarget = c; return true; }
                    }
                }
            }
            return super.dispatchTouchEvent(e);
        }
        if (touchTarget != null) {
            e.offsetLocation(-touchTarget.getLeft(), -touchTarget.getTop());
            boolean handled = touchTarget.dispatchTouchEvent(e);
            e.offsetLocation(touchTarget.getLeft(), touchTarget.getTop());
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) touchTarget = null;
            return handled;
        }
        return super.dispatchTouchEvent(e);
    }
}
