package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;

public class BitmapDrawable extends Drawable {
    private final Bitmap bitmap;

    public BitmapDrawable(Bitmap bitmap) { this.bitmap = bitmap; }
    public BitmapDrawable(Resources res, Bitmap bitmap) { this.bitmap = bitmap; }

    public final Bitmap getBitmap() { return bitmap; }
    @Override public int getIntrinsicWidth() { return bitmap.getWidth(); }
    @Override public int getIntrinsicHeight() { return bitmap.getHeight(); }

    @Override
    public void draw(Canvas canvas) { canvas.drawBitmap(bitmap, null, getBounds(), null); }
}
