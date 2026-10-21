package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

public class CheckBox extends CompoundButton {
    public CheckBox(Context c) { super(c); }
    public CheckBox(Context c, AttributeSet set) { super(c, set); }
    public CheckBox(Context c, AttributeSet set, int s) { super(c, set, s); }

    @Override
    void drawToggle(Canvas canvas, float x, float y) { box(canvas, x, y, isChecked(), false); }
}
