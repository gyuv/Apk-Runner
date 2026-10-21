package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Solid shapes: rectangle (optionally rounded) or oval, with optional stroke. */
public class GradientDrawable extends Drawable {
    public static final int RECTANGLE = 0, OVAL = 1, LINE = 2, RING = 3;

    private int shape = RECTANGLE;
    private int color;
    private float radius;
    private int strokeWidth, strokeColor;
    private int width = -1, height = -1;

    public GradientDrawable() {}
    public GradientDrawable(Object orientation, int[] colors) { if (colors != null && colors.length > 0) color = colors[0]; }

    public void setShape(int s) { shape = s; }
    public void setColor(int c) { color = c; invalidateSelf(); }
    public void setColors(int[] colors) { if (colors.length > 0) color = colors[0]; }
    public void setCornerRadius(float r) { radius = r; }
    public void setCornerRadii(float[] radii) { if (radii != null && radii.length > 0) radius = radii[0]; }
    public void setStroke(int w, int c) { strokeWidth = w; strokeColor = c; }
    public void setSize(int w, int h) { width = w; height = h; }
    @Override public int getIntrinsicWidth() { return width; }
    @Override public int getIntrinsicHeight() { return height; }

    @Override
    public void draw(Canvas canvas) {
        RectF r = new RectF(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom);
        Paint p = new Paint();
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) { if ((color >>> 24) == 0) continue; p.setStyle(Paint.Style.FILL); p.setColor(color); }
            else {
                if (strokeWidth <= 0) continue;
                p.setStyle(Paint.Style.STROKE); p.setColor(strokeColor); p.setStrokeWidth(strokeWidth);
                float h = strokeWidth / 2f;
                r = new RectF(r.left + h, r.top + h, r.right - h, r.bottom - h);
            }
            if (shape == OVAL) canvas.drawOval(r, p);
            else if (radius > 0) canvas.drawRoundRect(r, radius, radius, p);
            else canvas.drawRect(r, p);
        }
    }
}
