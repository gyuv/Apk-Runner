package android.graphics;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;

    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Align { LEFT, CENTER, RIGHT }

    private int color = Color.BLACK;
    private Style style = Style.FILL;
    private Align align = Align.LEFT;
    private float strokeWidth = 1f;
    private float textSize = 12f;
    private boolean bold;

    public Paint() {}
    public Paint(int flags) {}
    public Paint(Paint p) { color = p.color; style = p.style; align = p.align; strokeWidth = p.strokeWidth; textSize = p.textSize; bold = p.bold; }

    public void setColor(int c) { color = c; }
    public int getColor() { return color; }
    public void setAlpha(int a) { color = (color & 0x00FFFFFF) | (a << 24); }
    public void setStyle(Style s) { style = s; }
    public Style getStyle() { return style; }
    public void setStrokeWidth(float w) { strokeWidth = w; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setTextSize(float s) { textSize = s; }
    public float getTextSize() { return textSize; }
    public void setTextAlign(Align a) { align = a; }
    public Align getTextAlign() { return align; }
    public void setFakeBoldText(boolean b) { bold = b; }
    public boolean isFakeBoldText() { return bold; }
    public void setAntiAlias(boolean aa) {}

    public float measureText(String text) { return com.apkrunner.host.Fonts.width(text, textSize, bold); }
}
