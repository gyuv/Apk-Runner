package android.content;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import com.apkrunner.host.Host;
import com.apkrunner.res.Attrs;

public abstract class Context {
    private static final Resources RESOURCES = new Resources();

    public Resources getResources() { return RESOURCES; }
    public Context getApplicationContext() { return this; }
    public String getPackageName() { return Host.packageName(); }
    public static final String LAYOUT_INFLATER_SERVICE = "layout_inflater";

    public String getString(int id) { return getResources().getString(id); }
    public String getString(int id, Object... args) { return getResources().getString(id, args); }
    public CharSequence getText(int id) { return getResources().getText(id); }
    public int getColor(int id) { return getResources().getColor(id); }
    public Drawable getDrawable(int id) { return getResources().getDrawable(id); }
    public Resources.Theme getTheme() { return getResources().theme(); }

    public Object getSystemService(String name) {
        return LAYOUT_INFLATER_SERVICE.equals(name) ? LayoutInflater.from(this) : null;
    }

    public TypedArray obtainStyledAttributes(int[] attrs) { return new TypedArray(null, attrs); }
    public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs) { return new TypedArray(Attrs.of(set), attrs); }
    public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
        return new TypedArray(Attrs.of(set), attrs);
    }
    public TypedArray obtainStyledAttributes(int resid, int[] attrs) { return new TypedArray(null, attrs); }
    public void startActivity(Intent intent) { Host.startActivity(intent); }
}
