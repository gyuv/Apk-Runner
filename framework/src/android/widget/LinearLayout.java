package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0, VERTICAL = 1;

    public static class LayoutParams extends MarginLayoutParams {
        public float weight;
        public int gravity = -1;

        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, float weight) { super(w, h); this.weight = weight; }
        public LayoutParams(ViewGroup.LayoutParams src) { super(src); }
    }

    private int orientation = HORIZONTAL;
    private int gravity = Gravity.NO_GRAVITY;

    public LinearLayout(Context c) { super(c); }

    public void setOrientation(int o) { orientation = o; requestLayout(); }
    public int getOrientation() { return orientation; }
    public void setGravity(int g) { gravity = g; requestLayout(); }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return orientation == VERTICAL ? new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                : new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams ? p : new LayoutParams(p); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        boolean vert = orientation == VERTICAL;
        int used = 0, cross = 0;
        float totalWeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            totalWeight += lp.weight;
            if (vert) measureChildWithMargins(c, wSpec, 0, hSpec, used);
            else measureChildWithMargins(c, wSpec, used, hSpec, 0);
            used += vert ? c.getMeasuredHeight() + lp.topMargin + lp.bottomMargin
                    : c.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            cross = Math.max(cross, vert ? c.getMeasuredWidth() + lp.leftMargin + lp.rightMargin
                    : c.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }
        int mainSpec = vert ? hSpec : wSpec;
        int padMain = vert ? paddingTop + paddingBottom : paddingLeft + paddingRight;
        if (totalWeight > 0 && MeasureSpec.getMode(mainSpec) == MeasureSpec.EXACTLY) {
            int extra = MeasureSpec.getSize(mainSpec) - padMain - used;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                LayoutParams lp = (LayoutParams) c.getLayoutParams();
                if (c.getVisibility() == GONE || lp.weight <= 0) continue;
                int share = (int) (extra * lp.weight / totalWeight);
                int size = Math.max(0, (vert ? c.getMeasuredHeight() : c.getMeasuredWidth()) + share);
                int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
                if (vert) c.measure(MeasureSpec.makeMeasureSpec(c.getMeasuredWidth(), MeasureSpec.EXACTLY), exact);
                else c.measure(exact, MeasureSpec.makeMeasureSpec(c.getMeasuredHeight(), MeasureSpec.EXACTLY));
            }
            used = MeasureSpec.getSize(mainSpec) - padMain;
        }
        int w = vert ? cross + paddingLeft + paddingRight : used + padMain;
        int h = vert ? used + padMain : cross + paddingTop + paddingBottom;
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        boolean vert = orientation == VERTICAL;
        int innerW = r - l - paddingLeft - paddingRight, innerH = b - t - paddingTop - paddingBottom;
        int total = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            total += vert ? c.getMeasuredHeight() + lp.topMargin + lp.bottomMargin : c.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
        }
        int pos = vert ? paddingTop : paddingLeft;
        int mainGravity = vert ? gravity & 0x70 : gravity & 7;
        if (mainGravity == (vert ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL)) pos += ((vert ? innerH : innerW) - total) / 2;
        else if (mainGravity == (vert ? Gravity.BOTTOM : Gravity.RIGHT)) pos += (vert ? innerH : innerW) - total;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            int cw = c.getMeasuredWidth(), ch = c.getMeasuredHeight();
            int g = lp.gravity >= 0 ? lp.gravity : gravity;
            if (vert) {
                int x = paddingLeft + lp.leftMargin;
                if ((g & 7) == Gravity.CENTER_HORIZONTAL) x = paddingLeft + (innerW - cw) / 2;
                else if ((g & 7) == Gravity.RIGHT) x = paddingLeft + innerW - cw - lp.rightMargin;
                pos += lp.topMargin;
                c.layout(x, pos, x + cw, pos + ch);
                pos += ch + lp.bottomMargin;
            } else {
                int y = paddingTop + lp.topMargin;
                if ((g & 0x70) == Gravity.CENTER_VERTICAL) y = paddingTop + (innerH - ch) / 2;
                else if ((g & 0x70) == Gravity.BOTTOM) y = paddingTop + innerH - ch - lp.bottomMargin;
                pos += lp.leftMargin;
                c.layout(pos, y, pos + cw, y + ch);
                pos += cw + lp.rightMargin;
            }
        }
    }
}
