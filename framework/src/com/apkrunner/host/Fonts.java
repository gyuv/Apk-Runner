package com.apkrunner.host;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.HashMap;

/** Font lookup and metrics shared by Paint and Canvas. */
public final class Fonts {
    private static final HashMap<String, Font> CACHE = new HashMap<>();
    private static final java.awt.Graphics2D PROBE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

    public static Font font(float size, boolean bold) {
        return CACHE.computeIfAbsent(size + ":" + bold,
                k -> new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size));
    }

    private static FontMetrics metrics(float size, boolean bold) { return PROBE.getFontMetrics(font(size, bold)); }

    public static float width(String s, float size, boolean bold) { return metrics(size, bold).stringWidth(s); }
    public static float ascent(float size, boolean bold) { return metrics(size, bold).getAscent(); }
    public static float lineHeight(float size, boolean bold) { return metrics(size, bold).getHeight(); }
}
