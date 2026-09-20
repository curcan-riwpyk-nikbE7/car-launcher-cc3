package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.ViewGroup;

/**
 * Android 10 (API 29) system hidden ActivityView stub for compile-time linking.
 * At runtime, Android loads the real implementation from /system/framework/framework.jar.
 */
public class ActivityView extends ViewGroup {
    public ActivityView(Context context) {
        super(context);
    }

    public ActivityView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActivityView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static abstract class StateCallback {
        public abstract void onActivityViewReady(ActivityView view);
        public abstract void onActivityViewDestroyed(ActivityView view);
        public void onTaskCreated(int taskId, ComponentName componentName) {}
        public void onTaskMovedToFront(int taskId) {}
        public void onTaskRemovalStarted(int taskId) {}
    }

    public void setCallback(StateCallback callback) {}
    public void startActivity(Intent intent) {}
    public void release() {}
    public int getVirtualDisplayId() { return 0; }
    public void performBack() {}

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {}
}
