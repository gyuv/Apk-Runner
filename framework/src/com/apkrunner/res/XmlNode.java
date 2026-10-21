package com.apkrunner.res;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** A parsed Android binary XML element (layouts, drawables). */
public final class XmlNode {
    public static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public static final class Attr {
        public String ns, name, raw;
        public int resId, type, data;
    }

    public final String name;
    public final List<Attr> attrs = new ArrayList<>();
    public final List<XmlNode> children = new ArrayList<>();

    XmlNode(String name) { this.name = name; }

    public Attr attr(String name) {
        for (Attr a : attrs) if (a.name.equals(name) && (a.ns == null || a.ns.equals(ANDROID_NS) || a.ns.isEmpty())) return a;
        for (Attr a : attrs) if (a.name.equals(name)) return a;
        return null;
    }

    public Attr attrById(int resId) {
        for (Attr a : attrs) if (a.resId == resId) return a;
        return null;
    }

    public static XmlNode parse(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        StringPool strings = null;
        int[] resMap = new int[0];
        ArrayDeque<XmlNode> stack = new ArrayDeque<>();
        XmlNode root = null;
        int off = b.getShort(2) & 0xFFFF;
        while (off + 8 <= data.length) {
            int type = b.getShort(off) & 0xFFFF, size = b.getInt(off + 4);
            if (size <= 0) break;
            switch (type) {
                case 0x0001: strings = new StringPool(b, off); break;
                case 0x0180: {
                    int hs = b.getShort(off + 2) & 0xFFFF;
                    resMap = new int[(size - hs) / 4];
                    for (int i = 0; i < resMap.length; i++) resMap[i] = b.getInt(off + hs + 4 * i);
                    break;
                }
                case 0x0102: {
                    XmlNode n = new XmlNode(strings.get(b.getInt(off + 20)));
                    int attrStart = b.getShort(off + 24) & 0xFFFF, attrSize = b.getShort(off + 26) & 0xFFFF;
                    int count = b.getShort(off + 28) & 0xFFFF;
                    for (int i = 0; i < count; i++) {
                        int a = off + 16 + attrStart + i * attrSize;
                        Attr at = new Attr();
                        int ns = b.getInt(a), nameIdx = b.getInt(a + 4), raw = b.getInt(a + 8);
                        at.ns = ns == -1 ? null : strings.get(ns);
                        at.name = strings.get(nameIdx);
                        at.resId = nameIdx < resMap.length ? resMap[nameIdx] : 0;
                        at.raw = raw == -1 ? null : strings.get(raw);
                        at.type = b.get(a + 15) & 0xFF;
                        at.data = b.getInt(a + 16);
                        n.attrs.add(at);
                    }
                    if (stack.isEmpty()) root = n; else stack.peek().children.add(n);
                    stack.push(n);
                    break;
                }
                case 0x0103: stack.pop(); break;
                default: break;
            }
            off += size;
        }
        return root;
    }
}
