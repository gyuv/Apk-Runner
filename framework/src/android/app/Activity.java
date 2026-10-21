package android.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.apkrunner.host.Host;
import com.apkrunner.host.UiThread;

public class Activity extends Context {
    public static final int RESULT_OK = -1, RESULT_CANCELED = 0;

    private Intent intent = new Intent();
    private View content;
    private CharSequence title = "";
    private boolean finished;

    protected void onCreate(Bundle savedInstanceState) {}
    protected void onStart() {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onStop() {}
    protected void onDestroy() {}
    public void onBackPressed() { finish(); }

    public Intent getIntent() { return intent; }
    public void setIntent(Intent i) { intent = i; }
    public void setContentView(View v) { content = v; Host.onContentChanged(this); }
    public View getContentView() { return content; }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) { return content == null ? null : (T) content.findViewById(id); }

    public void setTitle(CharSequence t) { title = t; Host.onContentChanged(this); }
    public CharSequence getTitle() { return title; }
    public void runOnUiThread(Runnable r) { UiThread.post(r, 0); }
    public boolean isFinishing() { return finished; }
    public void finish() { finished = true; Host.finish(this); }

    // Called by the host to drive the lifecycle.
    public final void performCreate(Bundle b) { onCreate(b); onStart(); onResume(); }
    public final void performDestroy() { onPause(); onStop(); onDestroy(); }
}
