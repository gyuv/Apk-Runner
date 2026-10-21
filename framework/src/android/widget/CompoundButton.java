package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import com.apkrunner.res.Attrs;

/** Base for CheckBox, Switch, RadioButton: a toggle drawn left of its label. */
public abstract class CompoundButton extends Button {
    public interface OnCheckedChangeListener { void onCheckedChanged(CompoundButton b, boolean checked); }

    private boolean checked;
    private OnCheckedChangeListener listener;
    static final int BOX = 22;

    public CompoundButton(Context c) { this(c, null); }
    public CompoundButton(Context c, AttributeSet set) { this(c, set, 0); }

    public CompoundButton(Context c, AttributeSet set, int s) {
        super(c, set, s);
        Attrs a = Attrs.of(set);
        if (a == null || !a.has("textColor")) setTextColor(0xFF1D1B20);
        if (a == null || !a.has("textStyle")) getPaint().setFakeBoldText(false);
        if (a == null || !a.has("gravity")) setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        setPadding(BOX + 14, 8, 8, 8);
        checked = a != null && a.bool("checked", false);
    }

    public boolean isChecked() { return checked; }
    public void toggle() { setChecked(!checked); }
    public void setChecked(boolean c) {
        if (c == checked) return;
        checked = c;
        invalidate();
        if (listener != null) listener.onCheckedChanged(this, c);
    }
    public void setOnCheckedChangeListener(OnCheckedChangeListener l) { listener = l; }

    @Override
    public boolean performClick() { toggle(); return super.performClick(); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        if (e.getAction() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawToggle(canvas, 6, (getHeight() - BOX) / 2f);
        drawLabel(canvas);
    }

    void drawLabel(Canvas canvas) {
        // TextView's text drawing, skipping Button's filled background
        Paint p = getPaint();
        float y = (getHeight() - com.apkrunner.host.Fonts.lineHeight(p.getTextSize(), p.isFakeBoldText())) / 2f
                + com.apkrunner.host.Fonts.ascent(p.getTextSize(), p.isFakeBoldText());
        canvas.drawText(getText().toString(), paddingLeft, y, p);
    }

    abstract void drawToggle(Canvas canvas, float x, float y);

    static void box(Canvas canvas, float x, float y, boolean on, boolean round) {
        Paint p = new Paint();
        RectF r = new RectF(x, y, x + BOX, y + BOX);
        p.setColor(on ? 0xFF6750A4 : 0xFF49454F);
        p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
        p.setStrokeWidth(2);
        if (round) canvas.drawOval(r, p); else canvas.drawRoundRect(r, 3, 3, p);
        if (on) {
            p.setColor(0xFFFFFFFF);
            if (round) { p.setStyle(Paint.Style.FILL); canvas.drawCircle(x + BOX / 2f, y + BOX / 2f, 5, p); }
            else {
                p.setStrokeWidth(3);
                canvas.drawLine(x + 5, y + 11, x + 9, y + 16, p);
                canvas.drawLine(x + 9, y + 16, x + 17, y + 6, p);
            }
        }
    }
}
