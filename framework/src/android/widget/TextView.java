package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import com.apkrunner.host.Fonts;
import java.util.ArrayList;
import java.util.List;

public class TextView extends View {
    private CharSequence text = "";
    private final Paint paint = new Paint();
    private int gravity = Gravity.LEFT | Gravity.TOP;
    private List<String> lines = new ArrayList<>();

    public TextView(Context c) {
        super(c);
        paint.setTextSize(14 * c.getResources().getDisplayMetrics().scaledDensity);
        paint.setColor(0xFF202124);
    }

    public void setText(CharSequence t) { text = t == null ? "" : t; requestLayout(); invalidate(); }
    public void setText(int resId) { setText(getContext().getString(resId)); }
    public CharSequence getText() { return text; }
    public void append(CharSequence t) { setText(text.toString() + t); }
    public void setTextColor(int c) { paint.setColor(c); invalidate(); }
    public int getCurrentTextColor() { return paint.getColor(); }
    public void setTextSize(float sp) { paint.setTextSize(sp * getContext().getResources().getDisplayMetrics().scaledDensity); requestLayout(); }
    public void setTextSize(int unit, float size) { paint.setTextSize(android.util.TypedValue.applyDimension(unit, size, getContext().getResources().getDisplayMetrics())); requestLayout(); }
    public float getTextSize() { return paint.getTextSize(); }
    public void setGravity(int g) { gravity = g; invalidate(); }
    public int getGravity() { return gravity; }
    public void setTypeface(Object tf, int style) { paint.setFakeBoldText((style & 1) != 0); requestLayout(); }
    protected Paint getPaint() { return paint; }

    private float lineHeight() { return Fonts.lineHeight(paint.getTextSize(), paint.isFakeBoldText()); }

    private List<String> wrap(int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String para : text.toString().split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (maxWidth > 0 && line.length() > 0 && paint.measureText(candidate) > maxWidth) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int padH = paddingLeft + paddingRight, padV = paddingTop + paddingBottom;
        int maxW = MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED ? 0 : MeasureSpec.getSize(wSpec) - padH;
        lines = wrap(maxW);
        float w = 0;
        for (String l : lines) w = Math.max(w, paint.measureText(l));
        int h = (int) Math.ceil(lines.size() * lineHeight());
        setMeasuredDimension(resolveSize((int) Math.ceil(w) + padH, wSpec), resolveSize(h + padV, hSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float lh = lineHeight();
        float blockH = lines.size() * lh;
        float innerW = getWidth() - paddingLeft - paddingRight, innerH = getHeight() - paddingTop - paddingBottom;
        float y = paddingTop;
        if ((gravity & 0x70) == Gravity.CENTER_VERTICAL) y += (innerH - blockH) / 2;
        else if ((gravity & 0x70) == Gravity.BOTTOM) y += innerH - blockH;
        Paint p = new Paint(paint);
        if (!isEnabled()) p.setColor(Color.GRAY);
        for (String l : lines) {
            float x = paddingLeft, lw = p.measureText(l);
            if ((gravity & 7) == Gravity.CENTER_HORIZONTAL) x += (innerW - lw) / 2;
            else if ((gravity & 7) == Gravity.RIGHT) x += innerW - lw;
            canvas.drawText(l, x, y + Fonts.ascent(p.getTextSize(), p.isFakeBoldText()), p);
            y += lh;
        }
    }
}
