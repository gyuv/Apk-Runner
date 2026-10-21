package com.apkrunner.host;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The Android main thread is the Swing event dispatch thread. */
public final class UiThread {
    private static final IdentityHashMap<Runnable, List<Timer>> PENDING = new IdentityHashMap<>();

    public static synchronized void post(Runnable r, long delayMs) {
        if (delayMs <= 0 && !Host.isHeadless()) {
            SwingUtilities.invokeLater(r);
            return;
        }
        Timer t = new Timer((int) Math.max(0, delayMs), null);
        t.setRepeats(false);
        t.addActionListener(e -> {
            synchronized (UiThread.class) {
                List<Timer> l = PENDING.get(r);
                if (l != null) { l.remove(t); if (l.isEmpty()) PENDING.remove(r); }
            }
            r.run();
            Host.frameDirty();
        });
        PENDING.computeIfAbsent(r, k -> new ArrayList<>()).add(t);
        t.start();
    }

    public static synchronized void cancel(Runnable r) {
        List<Timer> l = PENDING.remove(r);
        if (l != null) for (Timer t : l) t.stop();
    }

    public static synchronized void cancelAll() {
        for (List<Timer> l : PENDING.values()) for (Timer t : l) t.stop();
        PENDING.clear();
    }
}
