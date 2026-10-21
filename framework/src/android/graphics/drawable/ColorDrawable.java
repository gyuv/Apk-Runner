package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Paint;

public class ColorDrawable extends Drawable {
    private int color;

    public ColorDrawable() {}
    public ColorDrawable(int color) { this.color = color; }

    public int getColor() { return color; }
    public void setColor(int c) { color = c; invalidateSelf(); }
    @Override public void setAlpha(int a) { color = (color & 0xFFFFFF) | (a << 24); }
    @Override public int getAlpha() { return color >>> 24; }

    @Override
    public void draw(Canvas canvas) {
        if ((color >>> 24) == 0) return;
        Paint p = new Paint();
        p.setColor(color);
        canvas.drawRect(getBounds(), p);
    }
}
