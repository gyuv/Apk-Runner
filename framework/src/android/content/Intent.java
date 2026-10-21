package android.content;

import android.os.Bundle;

public class Intent {
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";

    private String action;
    private String className;
    private final Bundle extras = new Bundle();

    public Intent() {}
    public Intent(String action) { this.action = action; }
    public Intent(Context ctx, Class<?> cls) { this.className = cls.getName(); }

    public String getAction() { return action; }
    public Intent setAction(String a) { action = a; return this; }
    public String getClassName() { return className; }
    public Intent setClassName(Context ctx, String cls) { className = cls; return this; }
    public Bundle getExtras() { return extras; }
    public Intent putExtra(String k, String v) { extras.putString(k, v); return this; }
    public Intent putExtra(String k, int v) { extras.putInt(k, v); return this; }
    public Intent putExtra(String k, long v) { extras.putLong(k, v); return this; }
    public Intent putExtra(String k, boolean v) { extras.putBoolean(k, v); return this; }
    public Intent putExtra(String k, float v) { extras.putFloat(k, v); return this; }
    public String getStringExtra(String k) { return extras.getString(k); }
    public int getIntExtra(String k, int d) { return extras.getInt(k, d); }
    public long getLongExtra(String k, long d) { return extras.getLong(k, d); }
    public boolean getBooleanExtra(String k, boolean d) { return extras.getBoolean(k, d); }
    public float getFloatExtra(String k, float d) { return extras.getFloat(k, d); }
}
