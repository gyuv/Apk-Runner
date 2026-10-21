package android.content;

import android.content.res.Resources;
import com.apkrunner.host.Host;

public abstract class Context {
    private static final Resources RESOURCES = new Resources();

    public Resources getResources() { return RESOURCES; }
    public Context getApplicationContext() { return this; }
    public String getPackageName() { return Host.packageName(); }
    public String getString(int id) { return getResources().getString(id); }
    public Object getSystemService(String name) { return null; }
    public void startActivity(Intent intent) { Host.startActivity(intent); }
}
