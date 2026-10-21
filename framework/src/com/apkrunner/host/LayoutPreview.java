package com.apkrunner.host;

import android.app.Activity;
import android.os.Bundle;

/** Shows one layout resource from the APK by name (apkrunner preview). */
public class LayoutPreview extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        String name = getIntent().getStringExtra("layout");
        int id = getResources().getIdentifier(name, "layout", getPackageName());
        if (id == 0) throw new IllegalArgumentException("No layout named " + name + " in this APK");
        setTitle("layout/" + name);
        setContentView(id);
    }
}
