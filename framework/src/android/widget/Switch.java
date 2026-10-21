package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

public class Switch extends CompoundButton {
    public Switch(Context c) { super(c); }
    public Switch(Context c, AttributeSet set) { super(c, set); }
    public Switch(Context c, AttributeSet set, int s) { super(c, set, s); setPadding(8, 8, 60, 8); }

    @Override
    void drawToggle(Canvas canvas, float x, float y) {
        float tx = getWidth() - 50, ty = (getHeight() - 26) / 2f;
        Paint p = new Paint();
        p.setColor(isChecked() ? 0xFF6750A4 : 0xFFE6E0E9);
        canvas.drawRoundRect(new RectF(tx, ty, tx + 44, ty + 26), 13, 13, p);
        p.setColor(isChecked() ? 0xFFFFFFFF : 0xFF79747E);
        canvas.drawCircle(isChecked() ? tx + 31 : tx + 13, ty + 13, isChecked() ? 10 : 7, p);
    }

    @Override
    void drawLabel(Canvas canvas) {
        int saved = paddingLeft;
        paddingLeft = 8;
        super.drawLabel(canvas);
        paddingLeft = saved;
    }
}
