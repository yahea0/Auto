package com.example.myautomationapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import java.util.Random;

/**
 * محرك النقر عالي الاستجابة (Macrorify Ultra-Responsive Gesture Engine)
 * زمن استجابة 25ms، مع مستمع اكتمال الحركة وإزاحة السنتر البشرية الذكية.
 */
public class AutoAccessibilityService extends AccessibilityService {
    public static AutoAccessibilityService instance;
    private static final Random random = new Random();

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
     * نقرة Macrorify السريعة الفورية (25ms) مع إزاحة عشوائية بشرية طفيفة (±2 بكسل)
     */
    public void click(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        // إزاحة بشرية ذكية لمنع كشف البوتات في الألعاب
        int jitterX = x + (random.nextInt(5) - 2);
        int jitterY = y + (random.nextInt(5) - 2);

        Path path = new Path();
        path.moveTo(jitterX, jitterY);
        path.lineTo(jitterX, jitterY);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 25));

        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
            }
        }, null);
    }

    /**
     * نقرة مزدوجة سريعة (Double Click)
     */
    public void doubleClick(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 25));
        builder.addStroke(new GestureDescription.StrokeDescription(path, 60, 25));
        dispatchGesture(builder.build(), null, null);
    }

    /**
     * نقرة ثلاثية (Triple Click)
     */
    public void tripleClick(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 25));
        builder.addStroke(new GestureDescription.StrokeDescription(path, 55, 25));
        builder.addStroke(new GestureDescription.StrokeDescription(path, 110, 25));
        dispatchGesture(builder.build(), null, null);
    }

    /**
     * ضغطة مطولة (Long Click)
     */
    public void longClick(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 400));
        dispatchGesture(builder.build(), null, null);
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
}
