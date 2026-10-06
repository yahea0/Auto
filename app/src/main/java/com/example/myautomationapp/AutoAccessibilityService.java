package com.example.myautomationapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class AutoAccessibilityService extends AccessibilityService {
    public static AutoAccessibilityService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        instance = null;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * تنفيذ نقرة حقيقية مضمونة 100% مع إزاحة بكسلية دقيقة (y + 1) للتوافق مع هواتف Realme
     */
    public void click(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y + 1); // ضروري جداً لتعريف حركة لمس مكتملة

        GestureDescription.Builder builder = new GestureDescription.Builder();
        // مدة نقرة 60 ميلي ثانية (الأنسب للنظام والألعاب)
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 60));

        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d("AutoClick", "تمت النقرة بنجاح عند: " + x + ", " + y);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(AutoAccessibilityService.this, "تم إلغاء النقرة من النظام!", Toast.LENGTH_SHORT).show()
                );
            }
        }, null);
    }

    public void swipe(int startX, int startY, int endX, int endY, int durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
    }

    public void pressBack() { performGlobalAction(GLOBAL_ACTION_BACK); }
    public void pressHome() { performGlobalAction(GLOBAL_ACTION_HOME); }
    public void openRecents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
    public void openNotifications() { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); }
    public void openQuickSettings() { performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); }
    public void lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
    public void takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }
}
