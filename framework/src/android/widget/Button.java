package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;

public class Button extends TextView {
    private static final int FILL = 0xFF6750A4, FILL_PRESSED = 0xFF4F378B, FILL_DISABLED = 0xFFCAC4D0;

    public Button(Context c) {
        super(c);
        setTextColor(0xFFFFFFFF);
        setGravity(Gravity.CENTER);
        getPaint().setFakeBoldText(true);
        setPadding(24, 12, 24, 12);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint p = new Paint();
        p.setColor(!isEnabled() ? FILL_DISABLED : isPressed() ? FILL_PRESSED : FILL);
        float r = getHeight() / 2f;
        canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), r, r, p);
        super.onDraw(canvas);
    }
}
