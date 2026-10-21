package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.util.AttributeSet;
import com.apkrunner.res.Attrs;

public class Button extends TextView {
    private static final int FILL = 0xFF6750A4, FILL_PRESSED = 0xFF4F378B, FILL_DISABLED = 0xFFCAC4D0;

    public Button(Context c) { this(c, null); }
    public Button(Context c, AttributeSet set) { this(c, set, 0); }

    public Button(Context c, AttributeSet set, int s) {
        super(c, set, s);
        Attrs a = Attrs.of(set);
        if (a == null || !a.has("textColor")) setTextColor(0xFFFFFFFF);
        if (a == null || !a.has("gravity")) setGravity(Gravity.CENTER);
        if (a == null || !a.has("textStyle")) getPaint().setFakeBoldText(true);
        if (a == null || !(a.has("padding") || a.has("paddingLeft") || a.has("paddingTop")))
            setPadding(24, 12, 24, 12);
        if (getMinimumHeight() == 0) setMinimumHeight(40);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getBackground() != null) { super.onDraw(canvas); return; } // app-styled button
        Paint p = new Paint();
        p.setColor(!isEnabled() ? FILL_DISABLED : isPressed() ? FILL_PRESSED : FILL);
        float r = getHeight() / 2f;
        canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), r, r, p);
        super.onDraw(canvas);
    }
}
