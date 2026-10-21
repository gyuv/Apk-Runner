package android.view;

import android.content.Context;
import android.util.AttributeSet;
import com.apkrunner.res.Attrs;
import com.apkrunner.res.XmlNode;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

/** Inflates compiled layout XML into views. */
public class LayoutInflater {
    private static final String[] PREFIXES = {"android.widget.", "android.view.", "android.webkit."};
    private static final Set<String> WARNED = new HashSet<>();

    private final Context context;

    protected LayoutInflater(Context context) { this.context = context; }

    public static LayoutInflater from(Context context) { return new LayoutInflater(context); }

    public Context getContext() { return context; }

    public View inflate(int resource, ViewGroup root) { return inflate(resource, root, root != null); }

    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        return inflate(context.getResources().getLayoutNode(resource), root, attachToRoot);
    }

    public View inflate(XmlNode node, ViewGroup root, boolean attachToRoot) {
        if (node.name.equals("merge")) {
            if (root == null) throw new InflateException("<merge> needs a root ViewGroup");
            inflateChildren(node, root);
            return root;
        }
        Attrs attrs = new Attrs(node, context.getResources());
        View view = createView(node, attrs);
        ViewGroup.LayoutParams lp = root != null ? root.generateLayoutParams(attrs) : null;
        if (view instanceof ViewGroup) inflateChildren(node, (ViewGroup) view);
        if (root != null && attachToRoot) {
            root.addView(view, lp);
            return root;
        }
        if (lp != null) view.setLayoutParams(lp);
        return view;
    }

    private void inflateChildren(XmlNode parent, ViewGroup group) {
        for (XmlNode child : parent.children) {
            switch (child.name) {
                case "requestFocus":
                case "tag":
                    continue;
                case "include": {
                    Attrs a = new Attrs(child, context.getResources());
                    int layout = a.resourceId("layout");
                    if (layout == 0) continue;
                    XmlNode included = context.getResources().getLayoutNode(layout);
                    if (included.name.equals("merge")) { inflateChildren(included, group); continue; }
                    View v = inflate(included, group, false);
                    if (a.resourceId("id") != 0) v.setId(a.resourceId("id"));
                    if (a.has("layout_width") && a.has("layout_height")) v.setLayoutParams(group.generateLayoutParams(a));
                    group.addView(v);
                    continue;
                }
                default: {
                    Attrs attrs = new Attrs(child, context.getResources());
                    View v = createView(child, attrs);
                    ViewGroup.LayoutParams lp = group.generateLayoutParams(attrs);
                    if (v instanceof ViewGroup) inflateChildren(child, (ViewGroup) v);
                    group.addView(v, lp);
                }
            }
        }
    }

    private View createView(XmlNode node, Attrs attrs) {
        String name = node.name;
        if (name.equals("view")) {
            XmlNode.Attr cls = node.attr("class");
            if (cls != null && cls.raw != null) name = cls.raw;
        }
        if (name.equals("fragment") || name.endsWith("FragmentContainerView")) return placeholder(name, attrs, false);
        Class<?> cls = null;
        if (name.indexOf('.') >= 0) {
            cls = load(name);
        } else {
            for (String p : PREFIXES) if ((cls = load(p + name)) != null) break;
        }
        if (cls == null) return placeholder(name, attrs, !node.children.isEmpty());
        try {
            Constructor<?> c = cls.getConstructor(Context.class, AttributeSet.class);
            return (View) c.newInstance(context, attrs);
        } catch (NoSuchMethodException e) {
            throw new InflateException("Binary XML: " + name + " has no (Context, AttributeSet) constructor");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            if (t instanceof Error) throw (Error) t;
            throw new InflateException("Error inflating " + name + ": " + t);
        } catch (ReflectiveOperationException e) {
            throw new InflateException("Error inflating " + name + ": " + e);
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** Stand-in for a view class that isn't implemented yet, so the rest of the screen still shows. */
    private View placeholder(String name, Attrs attrs, boolean container) {
        if (WARNED.add(name)) System.err.println("W/apkrunner: <" + name + "> is not supported yet; showing an empty placeholder");
        return container ? new android.widget.FrameLayout(context, attrs) : new View(context, attrs);
    }
}
