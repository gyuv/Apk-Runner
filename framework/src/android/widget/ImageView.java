package android.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import com.apkrunner.res.Attrs;

public class ImageView extends View {
    public enum ScaleType { MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE }

    private Drawable drawable;
    private ScaleType scaleType = ScaleType.FIT_CENTER;

    public ImageView(Context c) { this(c, null); }
    public ImageView(Context c, AttributeSet set) { this(c, set, 0); }

    public ImageView(Context c, AttributeSet set, int s) {
        super(c, set, s);
        Attrs a = Attrs.of(set);
        if (a == null) return;
        drawable = a.drawable("src");
        if (drawable == null) drawable = a.drawable("srcCompat");
        int st = a.intValue("scaleType", -1);
        if (st >= 0 && st < ScaleType.values().length) scaleType = ScaleType.values()[st];
    }

    public void setImageDrawable(Drawable d) { drawable = d; requestLayout(); invalidate(); }
    public void setImageBitmap(Bitmap b) { setImageDrawable(b == null ? null : new BitmapDrawable(b)); }
    public void setImageResource(int id) { setImageDrawable(id == 0 ? null : getContext().getResources().getDrawable(id)); }
    public Drawable getDrawable() { return drawable; }
    public void setScaleType(ScaleType t) { scaleType = t; invalidate(); }
    public ScaleType getScaleType() { return scaleType; }
    public void setAdjustViewBounds(boolean b) {}
    public void setColorFilter(int color) {}
    public void setImageTintList(Object tint) {}

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = drawable == null ? 0 : Math.max(0, drawable.getIntrinsicWidth());
        int h = drawable == null ? 0 : Math.max(0, drawable.getIntrinsicHeight());
        setMeasuredDimension(resolveSize(Math.max(getSuggestedMinimumWidth(), w + paddingLeft + paddingRight), wSpec),
                resolveSize(Math.max(getSuggestedMinimumHeight(), h + paddingTop + paddingBottom), hSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawable == null) return;
        int vw = getWidth() - paddingLeft - paddingRight, vh = getHeight() - paddingTop - paddingBottom;
        int dw = drawable.getIntrinsicWidth(), dh = drawable.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0 || scaleType == ScaleType.FIT_XY) {
            drawable.setBounds(paddingLeft, paddingTop, paddingLeft + vw, paddingTop + vh);
        } else {
            float s;
            switch (scaleType) {
                case CENTER: s = 1; break;
                case CENTER_CROP: s = Math.max((float) vw / dw, (float) vh / dh); break;
                case CENTER_INSIDE: s = Math.min(1f, Math.min((float) vw / dw, (float) vh / dh)); break;
                default: s = Math.min((float) vw / dw, (float) vh / dh);
            }
            int w = Math.round(dw * s), h = Math.round(dh * s);
            int x = paddingLeft + (scaleType == ScaleType.FIT_START ? 0 : scaleType == ScaleType.FIT_END ? vw - w : (vw - w) / 2);
            int y = paddingTop + (scaleType == ScaleType.FIT_START ? 0 : scaleType == ScaleType.FIT_END ? vh - h : (vh - h) / 2);
            drawable.setBounds(x, y, x + w, y + h);
        }
        canvas.save();
        canvas.clipRect(paddingLeft, paddingTop, paddingLeft + vw, paddingTop + vh);
        drawable.draw(canvas);
        canvas.restore();
    }
}
