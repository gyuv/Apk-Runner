package com.apkrunner.host;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Hosts Android activities in a desktop window.
 *
 * Usage: Host [--apk app.apk] [--package P] [--size WxH] [--screenshot out.png] [--tap X,Y] [--type TEXT]...
 *             (ActivityClass | --layout NAME)
 */
public final class Host {
    private static String packageName = "app";
    private static int width = 400, height = 720;
    private static boolean headless;
    private static final ArrayDeque<Activity> STACK = new ArrayDeque<>();
    private static JFrame frame;
    private static JPanel panel;
    private static boolean layoutNeeded = true;
    private static String toastText;
    private static long toastUntil;

    public static String packageName() { return packageName; }
    public static boolean isHeadless() { return headless; }

    public static void main(String[] args) throws Exception {
        // UTF-8 console output on every OS (Windows defaults to a legacy code page)
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        String activity = null, screenshot = null, layout = null;
        boolean dump = false;
        List<Object> script = new ArrayList<>(); // int[] taps and String typing, in order
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--package": packageName = args[++i]; break;
                case "--size": {
                    String[] wh = args[++i].split("x");
                    width = Integer.parseInt(wh[0]);
                    height = Integer.parseInt(wh[1]);
                    break;
                }
                case "--screenshot": screenshot = args[++i]; headless = true; break;
                case "--apk": Apk.open(args[++i]); break;
                case "--type": script.add(args[++i]); break;
                case "--layout": layout = args[++i]; activity = LayoutPreview.class.getName(); break;
                case "--dump": dump = true; break;
                case "--tap": {
                    String[] xy = args[++i].split(",");
                    script.add(new int[] {Integer.parseInt(xy[0]), Integer.parseInt(xy[1])});
                    break;
                }
                default: activity = args[i];
            }
        }
        if (activity == null) {
            System.err.println("usage: Host [--package P] [--size WxH] [--screenshot out.png] [--tap X,Y] ActivityClass");
            System.exit(2);
        }
        if (headless) System.setProperty("java.awt.headless", "true");
        final String cls = activity;
        final String shot = screenshot;
        final String previewLayout = layout;
        final boolean dumpText = dump;
        SwingUtilities.invokeAndWait(() -> {
            if (!headless) openWindow();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(null, cls);
            if (previewLayout != null) intent.putExtra("layout", previewLayout);
            launch(intent);
        });
        if (shot != null) {
            for (Object step : script) {
                SwingUtilities.invokeAndWait(() -> {
                    if (step instanceof int[]) {
                        int[] t = (int[]) step;
                        dispatch(MotionEvent.ACTION_DOWN, t[0], t[1]);
                        dispatch(MotionEvent.ACTION_UP, t[0], t[1]);
                    } else {
                        for (char c : ((String) step).toCharArray()) key(c, false, false);
                    }
                });
            }
            Thread.sleep(300);
            SwingUtilities.invokeAndWait(() -> {
                layoutIfNeeded(width, height);
                if (dumpText && root() != null) dumpTree(root(), 0);
                try {
                    ImageIO.write(render(), "png", new File(shot));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            System.exit(0);
        }
    }

    // ---- activities -----------------------------------------------------------

    private static void launch(Intent intent) {
        Activity a;
        try {
            a = (Activity) Class.forName(intent.getClassName()).getDeclaredConstructor().newInstance();
        } catch (NoClassDefFoundError e) {
            throw missing(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot create activity " + intent.getClassName(), e);
        }
        a.setIntent(intent);
        STACK.push(a);
        try {
            a.performCreate(null);
        } catch (NoClassDefFoundError e) {
            throw missing(e);
        }
        onContentChanged(a);
    }

    private static RuntimeException missing(NoClassDefFoundError e) {
        String name = e.getMessage() == null ? "?" : e.getMessage().replace('/', '.');
        return new UnsupportedOperationException(
                "This app needs the Android class " + name + ", which Apk-Runner does not implement yet.", e);
    }

    public static void startActivity(Intent intent) { UiThread.post(() -> launch(intent), 0); }

    public static void finish(Activity a) {
        UiThread.post(() -> {
            STACK.remove(a);
            a.performDestroy();
            if (STACK.isEmpty()) {
                if (frame != null) frame.dispose();
                if (!headless) System.exit(0);
            } else {
                onContentChanged(STACK.peek());
            }
        }, 0);
    }

    public static void onContentChanged(Activity a) {
        if (a != STACK.peek()) return;
        if (frame != null) frame.setTitle(a.getTitle() == null || a.getTitle().length() == 0 ? packageName : a.getTitle().toString());
        requestLayout();
    }

    // ---- rendering --------------------------------------------------------------

    public static void requestLayout() { layoutNeeded = true; frameDirty(); }
    public static void invalidate() { frameDirty(); }
    public static void frameDirty() { if (panel != null) panel.repaint(); }

    private static View root() {
        Activity a = STACK.peek();
        return a == null ? null : a.getContentView();
    }

    private static void layoutIfNeeded(int w, int h) {
        View root = root();
        if (root == null) return;
        if (layoutNeeded || root.getWidth() != w || root.getHeight() != h) {
            layoutNeeded = false;
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
            android.view.ViewTreeObserver.dispatchGlobalLayout();
        }
    }

    private static void paint(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        layoutIfNeeded(w, h);
        View root = root();
        if (root != null) {
            g.setClip(0, 0, w, h);
            root.draw(new Canvas(g, w, h));
        }
        if (toastText != null && System.currentTimeMillis() < toastUntil) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            int tw = g.getFontMetrics().stringWidth(toastText) + 32;
            int x = (w - tw) / 2, y = h - 80;
            g.setColor(new Color(0x32, 0x32, 0x32, 230));
            g.fillRoundRect(x, y, tw, 36, 36, 36);
            g.setColor(Color.WHITE);
            g.drawString(toastText, x + 16, y + 23);
        }
    }

    private static BufferedImage render() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        paint(g, width, height);
        g.dispose();
        return img;
    }

    public static void toast(String text, int ms) {
        toastText = text;
        toastUntil = System.currentTimeMillis() + ms;
        frameDirty();
        if (!headless) UiThread.post(Host::frameDirty, ms + 50);
    }

    /** Prints the view tree with texts, for tests and debugging. */
    private static void dumpTree(View v, int depth) {
        if (v.getVisibility() == View.GONE) return;
        StringBuilder sb = new StringBuilder("VIEW ");
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(v.getClass().getSimpleName()).append(" [").append(v.getLeft()).append(',').append(v.getTop())
                .append(' ').append(v.getWidth()).append('x').append(v.getHeight()).append(']');
        if (v instanceof android.widget.TextView) sb.append(" \"").append(((android.widget.TextView) v).getText()).append('"');
        if (v instanceof android.widget.CompoundButton) sb.append(((android.widget.CompoundButton) v).isChecked() ? " checked" : " unchecked");
        System.out.println(sb);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) dumpTree(g.getChildAt(i), depth + 1);
        }
    }

    // ---- keyboard focus and scrolling ----------------------------------------------

    private static View focused;

    public static void focus(View v) { focused = v; frameDirty(); }
    public static View focused() { return focused; }

    private static void key(char c, boolean backspace, boolean enter) {
        if (focused instanceof android.widget.EditText) ((android.widget.EditText) focused).onHostKey(c, backspace, enter);
        frameDirty();
    }

    /** Deepest ScrollView under (x, y), for the mouse wheel. */
    private static android.widget.ScrollView scrollTarget(View v, int x, int y) {
        android.widget.ScrollView found = null;
        if (v instanceof android.widget.ScrollView) {
            found = (android.widget.ScrollView) v;
            y += found.getScrollY();
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View c = g.getChildAt(i);
                if (c.getVisibility() == View.VISIBLE && x >= c.getLeft() && x < c.getRight() && y >= c.getTop() && y < c.getBottom()) {
                    android.widget.ScrollView inner = scrollTarget(c, x - c.getLeft(), y - c.getTop());
                    if (inner != null) return inner;
                    break;
                }
            }
        }
        return found;
    }

    private static void dispatch(int action, int x, int y) {
        if (panel != null) layoutIfNeeded(panel.getWidth(), panel.getHeight());
        else layoutIfNeeded(width, height);
        View root = root();
        if (root != null) root.dispatchTouchEvent(new MotionEvent(action, x, y));
        frameDirty();
    }

    private static void openWindow() {
        panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) { Host.paint((Graphics2D) g, getWidth(), getHeight()); }
        };
        panel.setPreferredSize(new Dimension(width, height));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dispatch(MotionEvent.ACTION_DOWN, e.getX(), e.getY()); }
            @Override public void mouseReleased(MouseEvent e) { dispatch(MotionEvent.ACTION_UP, e.getX(), e.getY()); }
            @Override public void mouseDragged(MouseEvent e) { dispatch(MotionEvent.ACTION_MOVE, e.getX(), e.getY()); }
        };
        panel.addMouseListener(mouse);
        panel.addMouseMotionListener(mouse);
        panel.addMouseWheelListener(e -> {
            View root = root();
            android.widget.ScrollView sv = root == null ? null : scrollTarget(root, e.getX(), e.getY());
            if (sv != null) { sv.scrollBy(0, (int) Math.round(e.getPreciseWheelRotation() * 40)); frameDirty(); }
        });
        new javax.swing.Timer(500, e -> { if (focused != null) frameDirty(); }).start(); // caret blink
        frame = new JFrame(packageName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(panel, BorderLayout.CENTER);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !STACK.isEmpty()) STACK.peek().onBackPressed();
                else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) key('\0', true, false);
                else if (e.getKeyCode() == KeyEvent.VK_ENTER) key('\0', false, true);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (c >= ' ' && c != 127) key(c, false, false);
            }
        });
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
