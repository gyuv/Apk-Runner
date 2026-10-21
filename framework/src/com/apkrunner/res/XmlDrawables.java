package com.apkrunner.res;

import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import java.util.ArrayList;
import java.util.List;

/** Inflates XML drawables: shape, selector (default state), layer-list, inset, ripple, bitmap, color. */
public final class XmlDrawables {
    public static Drawable inflate(XmlNode n, Resources res) {
        if (n == null) return new ColorDrawable(0);
        Attrs a = new Attrs(n, res);
        switch (n.name) {
            case "shape": return shape(n, a);
            case "color": return new ColorDrawable(a.color("color", 0));
            case "bitmap":
            case "nine-patch": {
                Drawable d = a.drawable("src");
                return d != null ? d : new ColorDrawable(0);
            }
            case "selector":
            case "animated-selector": {
                XmlNode pick = null;
                for (XmlNode item : n.children) {
                    if (!item.name.equals("item")) continue;
                    pick = item;
                    boolean stateful = false;
                    for (XmlNode.Attr at : item.attrs) if (at.name.startsWith("state_") && at.data != 0) stateful = true;
                    if (!stateful) break;
                }
                return pick == null ? new ColorDrawable(0) : item(pick, res);
            }
            case "layer-list":
            case "ripple": {
                List<Drawable> layers = new ArrayList<>();
                List<int[]> insets = new ArrayList<>();
                for (XmlNode item : n.children) {
                    if (!item.name.equals("item")) continue;
                    Attrs ia = new Attrs(item, res);
                    XmlNode.Attr id = item.attr("id");
                    if (n.name.equals("ripple") && id != null && id.data == 0x0102002e) continue; // @android:id/mask
                    layers.add(item(item, res));
                    insets.add(new int[] {(int) ia.dimension("left", 0), (int) ia.dimension("top", 0),
                            (int) ia.dimension("right", 0), (int) ia.dimension("bottom", 0)});
                }
                return new LayerDrawable(layers.toArray(new Drawable[0]), insets.toArray(new int[0][]));
            }
            case "inset": {
                Drawable d = a.drawable("drawable");
                if (d == null && !n.children.isEmpty()) d = inflate(n.children.get(0), res);
                float all = a.dimension("inset", 0);
                return new LayerDrawable(new Drawable[] {d}, new int[][] {{
                        (int) a.dimension("insetLeft", all), (int) a.dimension("insetTop", all),
                        (int) a.dimension("insetRight", all), (int) a.dimension("insetBottom", all)}});
            }
            default:
                return new ColorDrawable(0); // vector, animation-list, ... not supported yet
        }
    }

    private static Drawable item(XmlNode item, Resources res) {
        Drawable d = new Attrs(item, res).drawable("drawable");
        if (d == null && !item.children.isEmpty()) d = inflate(item.children.get(0), res);
        if (d == null) d = new Attrs(item, res).has("color") ? new ColorDrawable(new Attrs(item, res).color("color", 0)) : new ColorDrawable(0);
        return d;
    }

    private static Drawable shape(XmlNode n, Attrs a) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(a.intValue("shape", 0));
        for (XmlNode c : n.children) {
            Attrs ca = new Attrs(c, a.resources());
            switch (c.name) {
                case "solid": g.setColor(ca.color("color", 0)); break;
                case "corners": g.setCornerRadius(ca.dimension("radius", ca.dimension("topLeftRadius", 0))); break;
                case "stroke": g.setStroke((int) ca.dimension("width", 0), ca.color("color", 0)); break;
                case "size": g.setSize((int) ca.dimension("width", -1), (int) ca.dimension("height", -1)); break;
                case "gradient": g.setColor(ca.color("startColor", ca.color("centerColor", 0))); break;
                default: break;
            }
        }
        return g;
    }
}
