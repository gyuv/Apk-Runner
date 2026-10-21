package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.apkrunner.host.Fonts;
import com.apkrunner.host.Host;
import com.apkrunner.res.Attrs;
import java.util.ArrayList;
import java.util.List;

/** Single- or multi-line text field fed by the desktop keyboard. */
public class EditText extends TextView {
    private final List<TextWatcher> watchers = new ArrayList<>();
    private boolean password;

    public EditText(Context c) { this(c, null); }
    public EditText(Context c, AttributeSet set) { this(c, set, 0); }

    public EditText(Context c, AttributeSet set, int s) {
        super(c, set, s);
        Attrs a = Attrs.of(set);
        password = a != null && (a.intValue("inputType", 0) & 0xFF0) == 0x80;
        if (a == null || !(a.has("padding") || a.has("paddingTop"))) setPadding(4, 10, 4, 10);
        if (getMinimumHeight() == 0) setMinimumHeight(44);
        super.setText(new SpannableStringBuilder(super.getText()));
    }

    @Override
    public Editable getText() {
        CharSequence t = super.getText();
        return t instanceof Editable ? (Editable) t : new SpannableStringBuilder(t);
    }

    @Override
    public void setText(CharSequence t) {
        CharSequence old = super.getText();
        for (TextWatcher w : watchers) w.beforeTextChanged(old, 0, old.length(), t == null ? 0 : t.length());
        SpannableStringBuilder sb = new SpannableStringBuilder(t);
        super.setText(sb);
        for (TextWatcher w : watchers) w.onTextChanged(sb, 0, old.length(), sb.length());
        for (TextWatcher w : watchers) w.afterTextChanged(sb);
    }

    public void addTextChangedListener(TextWatcher w) { watchers.add(w); }
    public void removeTextChangedListener(TextWatcher w) { watchers.remove(w); }
    public void setSelection(int index) {}
    public void setInputType(int type) { password = (type & 0xFF0) == 0x80; invalidate(); }
    public boolean requestFocus() { Host.focus(this); return true; }
    public boolean isFocused() { return Host.focused() == this; }

    /** Keyboard input from the host window. */
    public void onHostKey(char c, boolean backspace, boolean enter) {
        String t = super.getText().toString();
        if (backspace) { if (!t.isEmpty()) setText(t.substring(0, t.length() - 1)); }
        else if (enter) setText(t + "\n");
        else setText(t + c);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && isEnabled()) requestFocus();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        CharSequence real = super.getText();
        if (password && real.length() > 0) {
            // draw dots instead of the text
            Paint p = new Paint(getPaint());
            String dots = "•".repeat(real.length());
            canvas.drawText(dots, paddingLeft, paddingTop + Fonts.ascent(p.getTextSize(), false), p);
        } else {
            super.onDraw(canvas);
        }
        Paint line = new Paint();
        boolean focused = isFocused();
        line.setColor(focused ? 0xFF6750A4 : 0xFF79747E);
        line.setStrokeWidth(focused ? 2 : 1);
        canvas.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2, line);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            String text = password ? "•".repeat(real.length()) : real.toString();
            String last = text.contains("\n") ? text.substring(text.lastIndexOf('\n') + 1) : text;
            float x = paddingLeft + getPaint().measureText(last) + 1;
            float lh = Fonts.lineHeight(getPaint().getTextSize(), false);
            int lineNo = text.split("\n", -1).length - 1;
            canvas.drawLine(x, paddingTop + lineNo * lh, x, paddingTop + (lineNo + 1) * lh, line);
        }
    }
}
