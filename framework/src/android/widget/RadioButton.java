package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

public class RadioButton extends CompoundButton {
    public RadioButton(Context c) { super(c); }
    public RadioButton(Context c, AttributeSet set) { super(c, set); }
    public RadioButton(Context c, AttributeSet set, int s) { super(c, set, s); }

    @Override
    void drawToggle(Canvas canvas, float x, float y) { box(canvas, x, y, isChecked(), true); }
}
