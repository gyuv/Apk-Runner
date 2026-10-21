package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class FrameLayout extends ViewGroup {
    public static class LayoutParams extends MarginLayoutParams {
        public int gravity = Gravity.NO_GRAVITY;

        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, int gravity) { super(w, h); this.gravity = gravity; }
        public LayoutParams(ViewGroup.LayoutParams src) { super(src); }
    }

    public FrameLayout(Context c) { super(c); }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT); }
    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams ? p : new LayoutParams(p); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = 0, h = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            measureChildWithMargins(c, wSpec, 0, hSpec, 0);
            MarginLayoutParams lp = (MarginLayoutParams) c.getLayoutParams();
            w = Math.max(w, c.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            h = Math.max(h, c.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }
        setMeasuredDimension(resolveSize(w + paddingLeft + paddingRight, wSpec), resolveSize(h + paddingTop + paddingBottom, hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int pw = r - l - paddingLeft - paddingRight, ph = b - t - paddingTop - paddingBottom;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            int cw = c.getMeasuredWidth(), ch = c.getMeasuredHeight();
            int x = paddingLeft + lp.leftMargin, y = paddingTop + lp.topMargin;
            int hg = lp.gravity & 7, vg = lp.gravity & 0x70;
            if (hg == Gravity.CENTER_HORIZONTAL) x = paddingLeft + (pw - cw) / 2;
            else if (hg == Gravity.RIGHT) x = paddingLeft + pw - cw - lp.rightMargin;
            if (vg == Gravity.CENTER_VERTICAL) y = paddingTop + (ph - ch) / 2;
            else if (vg == Gravity.BOTTOM) y = paddingTop + ph - ch - lp.bottomMargin;
            c.layout(x, y, x + cw, y + ch);
        }
    }
}
