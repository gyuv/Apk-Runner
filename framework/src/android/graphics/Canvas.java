package android.graphics;

import com.apkrunner.host.Fonts;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;

/** Canvas backed by Java2D. */
public class Canvas {
    private final Graphics2D g;
    private final ArrayDeque<Object[]> stack = new ArrayDeque<>();
    private final int width, height;

    public Canvas(Graphics2D g, int width, int height) { this.g = g; this.width = width; this.height = height; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public int save() {
        stack.push(new Object[] {g.getTransform(), g.getClip()});
        return stack.size();
    }

    public void restore() {
        Object[] s = stack.pop();
        g.setTransform((AffineTransform) s[0]);
        g.setClip((java.awt.Shape) s[1]);
    }

    public void translate(float dx, float dy) { g.translate(dx, dy); }
    public void scale(float sx, float sy) { g.scale(sx, sy); }
    public void rotate(float degrees) { g.rotate(Math.toRadians(degrees)); }
    public boolean clipRect(int l, int t, int r, int b) { g.clipRect(l, t, r - l, b - t); return true; }

    private void apply(Paint p) {
        g.setColor(new java.awt.Color(p.getColor(), true));
        g.setStroke(new BasicStroke(p.getStrokeWidth()));
    }

    private void shape(java.awt.Shape s, Paint p) {
        apply(p);
        if (p.getStyle() != Paint.Style.STROKE) g.fill(s);
        if (p.getStyle() != Paint.Style.FILL) g.draw(s);
    }

    public void drawColor(int color) {
        g.setColor(new java.awt.Color(color, true));
        g.fill(g.getClipBounds() != null ? g.getClipBounds() : new java.awt.Rectangle(0, 0, width, height));
    }

    public void drawRect(float l, float t, float r, float b, Paint p) { shape(new Rectangle2D.Float(l, t, r - l, b - t), p); }
    public void drawRect(Rect r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }
    public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }
    public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
        shape(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), p);
    }
    public void drawCircle(float cx, float cy, float radius, Paint p) {
        shape(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2), p);
    }
    public void drawOval(RectF r, Paint p) { shape(new Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p); }
    public void drawLine(float x1, float y1, float x2, float y2, Paint p) { apply(p); g.draw(new Line2D.Float(x1, y1, x2, y2)); }

    public void drawText(String text, float x, float y, Paint p) {
        apply(p);
        g.setFont(Fonts.font(p.getTextSize(), p.isFakeBoldText()));
        float w = p.measureText(text);
        if (p.getTextAlign() == Paint.Align.CENTER) x -= w / 2;
        else if (p.getTextAlign() == Paint.Align.RIGHT) x -= w;
        g.drawString(text, x, y);
    }
}
