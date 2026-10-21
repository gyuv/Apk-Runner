package android.widget;

import android.content.Context;
import com.apkrunner.host.Host;

public class Toast {
    public static final int LENGTH_SHORT = 0, LENGTH_LONG = 1;

    private final CharSequence text;
    private final int duration;

    private Toast(CharSequence text, int duration) { this.text = text; this.duration = duration; }

    public static Toast makeText(Context ctx, CharSequence text, int duration) { return new Toast(text, duration); }

    public void show() { Host.toast(String.valueOf(text), duration == LENGTH_LONG ? 3500 : 2000); }
}
