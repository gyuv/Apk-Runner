package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import com.apkrunner.res.Attrs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Positions children relative to the parent and to each other (by ID). */
public class RelativeLayout extends ViewGroup {
    public static final int TRUE = -1;
    public static final int LEFT_OF = 0, RIGHT_OF = 1, ABOVE = 2, BELOW = 3, ALIGN_BASELINE = 4, ALIGN_LEFT = 5,
            ALIGN_TOP = 6, ALIGN_RIGHT = 7, ALIGN_BOTTOM = 8, ALIGN_PARENT_LEFT = 9, ALIGN_PARENT_TOP = 10,
            ALIGN_PARENT_RIGHT = 11, ALIGN_PARENT_BOTTOM = 12, CENTER_IN_PARENT = 13, CENTER_HORIZONTAL = 14,
            CENTER_VERTICAL = 15, START_OF = 16, END_OF = 17, ALIGN_START = 18, ALIGN_END = 19,
            ALIGN_PARENT_START = 20, ALIGN_PARENT_END = 21;
    private static final int VERB_COUNT = 22;

    private static final String[] ATTRS = {"layout_toLeftOf", "layout_toRightOf", "layout_above", "layout_below",
            "layout_alignBaseline", "layout_alignLeft", "layout_alignTop", "layout_alignRight", "layout_alignBottom",
            "layout_alignParentLeft", "layout_alignParentTop", "layout_alignParentRight", "layout_alignParentBottom",
            "layout_centerInParent", "layout_centerHorizontal", "layout_centerVertical", "layout_toStartOf",
            "layout_toEndOf", "layout_alignStart", "layout_alignEnd", "layout_alignParentStart", "layout_alignParentEnd"};

    public static class LayoutParams extends MarginLayoutParams {
        private final int[] rules = new int[VERB_COUNT];
        int left, top, right, bottom;

        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(ViewGroup.LayoutParams src) {
            super(src);
            if (src instanceof LayoutParams) System.arraycopy(((LayoutParams) src).rules, 0, rules, 0, VERB_COUNT);
        }
        public LayoutParams(Context c, AttributeSet set) {
            super(c, set);
            Attrs a = Attrs.of(set);
            if (a == null) return;
            for (int v = 0; v < VERB_COUNT; v++) {
                if (!a.has(ATTRS[v])) continue;
                int ref = a.resourceId(ATTRS[v]);
                rules[v] = ref != 0 ? ref : a.bool(ATTRS[v], false) ? TRUE : 0;
            }
        }

        public void addRule(int verb) { rules[verb] = TRUE; }
        public void addRule(int verb, int subject) { rules[verb] = subject; }
        public void removeRule(int verb) { rules[verb] = 0; }
        public int getRule(int verb) { return rules[verb]; }
        public int[] getRules() { return rules; }

        /** Fold start/end rules into left/right (LTR only). */
        int rule(int verb) {
            switch (verb) {
                case LEFT_OF: return rules[LEFT_OF] != 0 ? rules[LEFT_OF] : rules[START_OF];
                case RIGHT_OF: return rules[RIGHT_OF] != 0 ? rules[RIGHT_OF] : rules[END_OF];
                case ALIGN_LEFT: return rules[ALIGN_LEFT] != 0 ? rules[ALIGN_LEFT] : rules[ALIGN_START];
                case ALIGN_RIGHT: return rules[ALIGN_RIGHT] != 0 ? rules[ALIGN_RIGHT] : rules[ALIGN_END];
                case ALIGN_PARENT_LEFT: return rules[ALIGN_PARENT_LEFT] != 0 ? rules[ALIGN_PARENT_LEFT] : rules[ALIGN_PARENT_START];
                case ALIGN_PARENT_RIGHT: return rules[ALIGN_PARENT_RIGHT] != 0 ? rules[ALIGN_PARENT_RIGHT] : rules[ALIGN_PARENT_END];
                default: return rules[verb];
            }
        }
    }

    private int gravity = Gravity.NO_GRAVITY;

    public RelativeLayout(Context c) { super(c); }
    public RelativeLayout(Context c, AttributeSet a) { this(c, a, 0); }
    public RelativeLayout(Context c, AttributeSet a, int s) {
        super(c, a, s);
        Attrs at = Attrs.of(a);
        if (at != null) gravity = at.intValue("gravity", Gravity.NO_GRAVITY);
    }

    public void setGravity(int g) { gravity = g; requestLayout(); }

    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams ? p : new LayoutParams(p); }
    @Override public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }

    /** Children ordered so every view comes after the views it is positioned against. */
    private List<View> sorted() {
        Map<Integer, View> byId = new HashMap<>();
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getId() != NO_ID) byId.put(getChildAt(i).getId(), getChildAt(i));
        List<View> out = new ArrayList<>();
        List<View> pending = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) pending.add(getChildAt(i));
        while (!pending.isEmpty()) {
            boolean progress = false;
            for (int i = 0; i < pending.size(); i++) {
                View v = pending.get(i);
                boolean ready = true;
                for (int r : ((LayoutParams) v.getLayoutParams()).rules) {
                    View dep = r > 0 ? byId.get(r) : null;
                    if (dep != null && dep != v && !out.contains(dep)) { ready = false; break; }
                }
                if (ready) { out.add(v); pending.remove(i--); progress = true; }
            }
            if (!progress) { out.addAll(pending); break; } // cycle: keep declaration order
        }
        return out;
    }

    private View anchor(int id) {
        if (id <= 0) return null;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getId() == id && v.getVisibility() != GONE) return v;
        }
        return null;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int wMode = MeasureSpec.getMode(wSpec), hMode = MeasureSpec.getMode(hSpec);
        int W = wMode == MeasureSpec.UNSPECIFIED ? 100000 : MeasureSpec.getSize(wSpec);
        int H = hMode == MeasureSpec.UNSPECIFIED ? 100000 : MeasureSpec.getSize(hSpec);
        int maxR = 0, maxB = 0;
        List<View> order = sorted();
        for (View c : order) {
            if (c.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            // horizontal constraints
            int l = Integer.MIN_VALUE, r = Integer.MIN_VALUE;
            View a;
            if ((a = anchor(lp.rule(LEFT_OF))) != null) r = ((LayoutParams) a.getLayoutParams()).left - ((MarginLayoutParams) a.getLayoutParams()).leftMargin - lp.rightMargin;
            if ((a = anchor(lp.rule(RIGHT_OF))) != null) l = ((LayoutParams) a.getLayoutParams()).right + ((MarginLayoutParams) a.getLayoutParams()).rightMargin + lp.leftMargin;
            if ((a = anchor(lp.rule(ALIGN_LEFT))) != null) l = ((LayoutParams) a.getLayoutParams()).left + lp.leftMargin;
            if ((a = anchor(lp.rule(ALIGN_RIGHT))) != null) r = ((LayoutParams) a.getLayoutParams()).right - lp.rightMargin;
            if (lp.rule(ALIGN_PARENT_LEFT) != 0) l = paddingLeft + lp.leftMargin;
            if (lp.rule(ALIGN_PARENT_RIGHT) != 0) r = W - paddingRight - lp.rightMargin;
            int t = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
            if ((a = anchor(lp.rule(ABOVE))) != null) b = ((LayoutParams) a.getLayoutParams()).top - ((MarginLayoutParams) a.getLayoutParams()).topMargin - lp.bottomMargin;
            if ((a = anchor(lp.rule(BELOW))) != null) t = ((LayoutParams) a.getLayoutParams()).bottom + ((MarginLayoutParams) a.getLayoutParams()).bottomMargin + lp.topMargin;
            if ((a = anchor(lp.rule(ALIGN_TOP))) != null) t = ((LayoutParams) a.getLayoutParams()).top + lp.topMargin;
            if ((a = anchor(lp.rule(ALIGN_BOTTOM))) != null) b = ((LayoutParams) a.getLayoutParams()).bottom - lp.bottomMargin;
            if ((a = anchor(lp.rule(ALIGN_BASELINE))) != null) t = ((LayoutParams) a.getLayoutParams()).top;
            if (lp.rule(ALIGN_PARENT_TOP) != 0) t = paddingTop + lp.topMargin;
            if (lp.rule(ALIGN_PARENT_BOTTOM) != 0) b = H - paddingBottom - lp.bottomMargin;

            int availL = l != Integer.MIN_VALUE ? l : paddingLeft + lp.leftMargin;
            int availR = r != Integer.MIN_VALUE ? r : W - paddingRight - lp.rightMargin;
            int availT = t != Integer.MIN_VALUE ? t : paddingTop + lp.topMargin;
            int availB = b != Integer.MIN_VALUE ? b : H - paddingBottom - lp.bottomMargin;
            c.measure(spec(lp.width, availR - availL, l != Integer.MIN_VALUE && r != Integer.MIN_VALUE, wMode),
                    spec(lp.height, availB - availT, t != Integer.MIN_VALUE && b != Integer.MIN_VALUE, hMode));
            int cw = c.getMeasuredWidth(), ch = c.getMeasuredHeight();

            boolean centerH = lp.rule(CENTER_IN_PARENT) != 0 || lp.rule(CENTER_HORIZONTAL) != 0;
            boolean centerV = lp.rule(CENTER_IN_PARENT) != 0 || lp.rule(CENTER_VERTICAL) != 0;
            if (centerH && wMode == MeasureSpec.EXACTLY) lp.left = (W - cw) / 2;
            else if (l == Integer.MIN_VALUE && r != Integer.MIN_VALUE) lp.left = r - cw;
            else if (l == Integer.MIN_VALUE && (gravity & 7) == Gravity.RIGHT && wMode == MeasureSpec.EXACTLY) lp.left = availR - cw;
            else lp.left = availL;
            if (centerV && hMode == MeasureSpec.EXACTLY) lp.top = (H - ch) / 2;
            else if (t == Integer.MIN_VALUE && b != Integer.MIN_VALUE) lp.top = b - ch;
            else lp.top = availT;
            lp.right = lp.left + cw;
            lp.bottom = lp.top + ch;
            maxR = Math.max(maxR, lp.right + lp.rightMargin);
            maxB = Math.max(maxB, lp.bottom + lp.bottomMargin);
        }
        int w = wMode == MeasureSpec.EXACTLY ? W : Math.min(maxR + paddingRight, W);
        int h = hMode == MeasureSpec.EXACTLY ? H : Math.min(maxB + paddingBottom, H);
        setMeasuredDimension(Math.max(w, getSuggestedMinimumWidth()), Math.max(h, getSuggestedMinimumHeight()));
        // wrap_content parent: now that its size is known, apply centering and right/bottom alignment
        for (View c : order) {
            if (c.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            int cw = c.getMeasuredWidth(), ch = c.getMeasuredHeight();
            boolean centerH = lp.rule(CENTER_IN_PARENT) != 0 || lp.rule(CENTER_HORIZONTAL) != 0;
            boolean centerV = lp.rule(CENTER_IN_PARENT) != 0 || lp.rule(CENTER_VERTICAL) != 0;
            if (wMode != MeasureSpec.EXACTLY) {
                if (centerH) lp.left = (w - cw) / 2;
                else if (lp.rule(ALIGN_PARENT_RIGHT) != 0 && lp.rule(ALIGN_PARENT_LEFT) == 0) lp.left = w - paddingRight - lp.rightMargin - cw;
                lp.right = lp.left + cw;
            }
            if (hMode != MeasureSpec.EXACTLY) {
                if (centerV) lp.top = (h - ch) / 2;
                else if (lp.rule(ALIGN_PARENT_BOTTOM) != 0 && lp.rule(ALIGN_PARENT_TOP) == 0) lp.top = h - paddingBottom - lp.bottomMargin - ch;
                lp.bottom = lp.top + ch;
            }
        }
    }

    private static int spec(int dim, int avail, boolean bothEdges, int parentMode) {
        avail = Math.max(0, avail);
        if (dim >= 0) return MeasureSpec.makeMeasureSpec(dim, MeasureSpec.EXACTLY);
        if (bothEdges || (dim == LayoutParams.MATCH_PARENT && parentMode != MeasureSpec.UNSPECIFIED))
            return MeasureSpec.makeMeasureSpec(avail, MeasureSpec.EXACTLY);
        return MeasureSpec.makeMeasureSpec(avail, parentMode == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            c.layout(lp.left, lp.top, lp.right, lp.bottom);
        }
    }
}
