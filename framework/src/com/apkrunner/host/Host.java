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
 * Usage: Host [--package P] [--size WxH] [--screenshot out.png] [--tap X,Y]... ActivityClass
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
        String activity = null, screenshot = null;
        List<int[]> taps = new ArrayList<>();
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
                case "--tap": {
                    String[] xy = args[++i].split(",");
                    taps.add(new int[] {Integer.parseInt(xy[0]), Integer.parseInt(xy[1])});
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
        SwingUtilities.invokeAndWait(() -> {
            if (!headless) openWindow();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(null, cls);
            launch(intent);
        });
        if (shot != null) {
            for (int[] t : taps) {
                SwingUtilities.invokeAndWait(() -> {
                    dispatch(MotionEvent.ACTION_DOWN, t[0], t[1]);
                    dispatch(MotionEvent.ACTION_UP, t[0], t[1]);
                });
            }
            Thread.sleep(300);
            SwingUtilities.invokeAndWait(() -> {
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
        frame = new JFrame(packageName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(panel, BorderLayout.CENTER);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !STACK.isEmpty()) STACK.peek().onBackPressed();
            }
        });
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
