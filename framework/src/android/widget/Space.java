package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

public final class Space extends View {
    public Space(Context c) { super(c); }
    public Space(Context c, AttributeSet a) { super(c, a); }
    public Space(Context c, AttributeSet a, int s) { super(c, a, s); }

    @Override public void draw(Canvas canvas) {}
}
