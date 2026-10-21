package android.graphics;

import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize = 1;
        public int outWidth, outHeight;
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
        public boolean inMutable;
        public boolean inScaled = true;
        public String outMimeType;
    }

    public static Bitmap decodeStream(InputStream in) { return decodeStream(in, null, null); }

    public static Bitmap decodeStream(InputStream in, Rect pad, Options opts) {
        try {
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            if (opts != null) {
                opts.outWidth = img.getWidth();
                opts.outHeight = img.getHeight();
                opts.outMimeType = "image/png";
                if (opts.inJustDecodeBounds) return null;
            }
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(img, 0, 0, null);
            Bitmap b = new Bitmap(argb);
            int s = opts == null ? 1 : Math.max(1, opts.inSampleSize);
            return s == 1 ? b : Bitmap.createScaledBitmap(b, b.getWidth() / s, b.getHeight() / s, true);
        } catch (IOException e) {
            return null;
        }
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) { return decodeByteArray(data, offset, length, null); }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) {
        return decodeStream(new ByteArrayInputStream(data, offset, length), null, opts);
    }

    public static Bitmap decodeFile(String path) { return decodeFile(path, null); }

    public static Bitmap decodeFile(String path, Options opts) {
        try (InputStream in = new java.io.FileInputStream(new File(path))) {
            return decodeStream(in, null, opts);
        } catch (IOException e) {
            return null;
        }
    }

    public static Bitmap decodeResource(Resources res, int id) { return decodeResource(res, id, null); }

    public static Bitmap decodeResource(Resources res, int id, Options opts) {
        Drawable d = res.getDrawable(id);
        return d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null;
    }
}
