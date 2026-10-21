package android.graphics;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE }
    public enum CompressFormat { JPEG, PNG, WEBP }

    private BufferedImage image;
    private final Config config;

    public Bitmap(BufferedImage image) { this(image, Config.ARGB_8888); }
    private Bitmap(BufferedImage image, Config config) { this.image = image; this.config = config; }

    public BufferedImage image() { return image; }

    public static Bitmap createBitmap(int w, int h, Config config) {
        return new Bitmap(new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB), config);
    }

    public static Bitmap createBitmap(Bitmap src) { return createScaledBitmap(src, src.getWidth(), src.getHeight(), false); }

    public static Bitmap createBitmap(Bitmap src, int x, int y, int w, int h) {
        BufferedImage sub = src.image.getSubimage(x, y, w, h);
        Bitmap b = createBitmap(w, h, src.config);
        b.image.getGraphics().drawImage(sub, 0, 0, null);
        return b;
    }

    public static Bitmap createScaledBitmap(Bitmap src, int w, int h, boolean filter) {
        Bitmap b = createBitmap(w, h, src.config);
        Graphics2D g = b.image.createGraphics();
        if (filter) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src.image, 0, 0, w, h, null);
        g.dispose();
        return b;
    }

    public int getWidth() { return image.getWidth(); }
    public int getHeight() { return image.getHeight(); }
    public Config getConfig() { return config; }
    public int getPixel(int x, int y) { return image.getRGB(x, y); }
    public void setPixel(int x, int y, int c) { image.setRGB(x, y, c); }
    public void getPixels(int[] px, int offset, int stride, int x, int y, int w, int h) { image.getRGB(x, y, w, h, px, offset, stride); }
    public void setPixels(int[] px, int offset, int stride, int x, int y, int w, int h) { image.setRGB(x, y, w, h, px, offset, stride); }
    public void eraseColor(int c) { Graphics2D g = image.createGraphics(); g.setComposite(java.awt.AlphaComposite.Src); g.setColor(new java.awt.Color(c, true)); g.fillRect(0, 0, getWidth(), getHeight()); g.dispose(); }
    public boolean hasAlpha() { return image.getColorModel().hasAlpha(); }
    public boolean isRecycled() { return image == null; }
    public void recycle() {}
    public Bitmap copy(Config c, boolean mutable) { return createScaledBitmap(this, getWidth(), getHeight(), false); }
    public boolean isMutable() { return true; }
    public int getByteCount() { return getWidth() * getHeight() * 4; }

    public boolean compress(CompressFormat format, int quality, java.io.OutputStream out) {
        try {
            return javax.imageio.ImageIO.write(image, format == CompressFormat.JPEG ? "jpg" : "png", out);
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
