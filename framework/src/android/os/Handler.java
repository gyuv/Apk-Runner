package android.os;

import com.apkrunner.host.UiThread;

public class Handler {
    public Handler() {}
    public Handler(Looper looper) {}

    public final boolean post(Runnable r) { UiThread.post(r, 0); return true; }
    public final boolean postDelayed(Runnable r, long delayMillis) { UiThread.post(r, delayMillis); return true; }
    public final void removeCallbacks(Runnable r) { UiThread.cancel(r); }
    public final void removeCallbacksAndMessages(Object token) { UiThread.cancelAll(); }
}
