package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

public class LayerDrawable extends Drawable {
    private final Drawable[] layers;
    private final int[][] insets;

    public LayerDrawable(Drawable[] layers) { this(layers, new int[layers.length][4]); }

    public LayerDrawable(Drawable[] layers, int[][] insets) { this.layers = layers; this.insets = insets; }

    public int getNumberOfLayers() { return layers.length; }
    public Drawable getDrawable(int i) { return layers[i]; }

    @Override
    protected void onBoundsChange(Rect b) {
        for (int i = 0; i < layers.length; i++) {
            if (layers[i] == null) continue;
            int[] in = insets[i];
            layers[i].setBounds(b.left + in[0], b.top + in[1], b.right - in[2], b.bottom - in[3]);
        }
    }

    @Override
    public int getIntrinsicWidth() { int w = -1; for (Drawable d : layers) if (d != null) w = Math.max(w, d.getIntrinsicWidth()); return w; }
    @Override
    public int getIntrinsicHeight() { int h = -1; for (Drawable d : layers) if (d != null) h = Math.max(h, d.getIntrinsicHeight()); return h; }

    @Override
    public void draw(Canvas canvas) { for (Drawable d : layers) if (d != null) d.draw(canvas); }
}
