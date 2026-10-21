package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Vertical scrolling container: mouse wheel (via the host) and drag. */
public class ScrollView extends FrameLayout {
    private int scrollY;
    private float downY, lastY;
    private boolean dragging;

    public ScrollView(Context c) { super(c); }
    public ScrollView(Context c, AttributeSet a) { super(c, a); }
    public ScrollView(Context c, AttributeSet a, int s) { super(c, a, s); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
        if (getChildCount() == 0) return;
        View c = getChildAt(0);
        int w = getMeasuredWidth() - paddingLeft - paddingRight;
        c.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) return;
        View c = getChildAt(0);
        c.layout(paddingLeft, paddingTop, paddingLeft + c.getMeasuredWidth(), paddingTop + c.getMeasuredHeight());
        scrollTo(0, scrollY);
    }

    private int maxScroll() {
        return getChildCount() == 0 ? 0 : Math.max(0, getChildAt(0).getHeight() + paddingTop + paddingBottom - getHeight());
    }

    public void scrollTo(int x, int y) { scrollY = Math.max(0, Math.min(maxScroll(), y)); invalidate(); }
    public void scrollBy(int x, int y) { scrollTo(0, scrollY + y); }
    public int getScrollY() { return scrollY; }
    public void fullScroll(int direction) { scrollTo(0, direction == 130 ? maxScroll() : 0); }
    public void smoothScrollTo(int x, int y) { scrollTo(x, y); }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(0, -scrollY);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: downY = lastY = e.getY(); dragging = false; break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getY() - downY) > 8) {
                    dragging = true;
                    MotionEvent cancel = new MotionEvent(MotionEvent.ACTION_CANCEL, e.getX(), e.getY() + scrollY);
                    super.dispatchTouchEvent(cancel);
                }
                if (dragging) { scrollBy(0, Math.round(lastY - e.getY())); lastY = e.getY(); return true; }
                break;
            default:
                if (dragging) return true;
        }
        e.offsetLocation(0, scrollY);
        boolean r = super.dispatchTouchEvent(e);
        e.offsetLocation(0, -scrollY);
        return r;
    }
}
