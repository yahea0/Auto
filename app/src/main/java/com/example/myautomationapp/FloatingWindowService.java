package com.example.myautomationapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;      // الزر الدائري
    private View menuView;          // القائمة المنفصلة
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams menuParams;
    private static final String CHANNEL_ID = "AutoServiceChannel";
    private boolean isMenuOpen = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            // استخدام دالة startForeground الأصلية في أندرويد بدون أخطاء توافق
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, buildNotification());
            }
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        new Handler(Looper.getMainLooper()).postDelayed(this::initViews, 500);
    }

    private void initViews() {
        // 1. تهيئة الزر الدائري
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        // 2. تهيئة القائمة المنفصلة
        menuView = LayoutInflater.from(this).inflate(R.layout.menu_layout, null);
        menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        menuParams.gravity = Gravity.CENTER;

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في إضافة النافذة العائمة", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        setupFloatingButtonLogic();
        setupMenuLogic();
    }

    private void setupFloatingButtonLogic() {
        View circleButton = floatingView.findViewById(R.id.circleButton);
        circleButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) isDragging = true;
                        if (isDragging) {
                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;

                            DisplayMetrics dm = getResources().getDisplayMetrics();
                            int viewWidth = floatingView.getWidth();
                            int viewHeight = floatingView.getHeight();
                            if (viewWidth > 0 && viewHeight > 0) {
                                if (params.x < 0) params.x = 0;
                                if (params.x > dm.widthPixels - viewWidth) params.x = dm.widthPixels - viewWidth;
                                if (params.y < 0) params.y = 0;
                                if (params.y > dm.heightPixels - viewHeight) params.y = dm.heightPixels - viewHeight;
                            }
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            toggleMenu();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleMenu() {
        if (isMenuOpen) {
            if (menuView.getWindowToken() != null) {
                windowManager.removeView(menuView);
            }
        } else {
            if (menuView.getWindowToken() == null) {
                windowManager.addView(menuView, menuParams);
            }
        }
        isMenuOpen = !isMenuOpen;
    }

    private void setupMenuLogic() {
        menuView.findViewById(R.id.menuManage).setOnClickListener(v -> {
            if (menuView.getWindowToken() != null) {
                windowManager.removeView(menuView);
                isMenuOpen = false;
            }
            Intent intent = new Intent(this, ActionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        menuView.findViewById(R.id.menuRunTest).setOnClickListener(v -> {
            Toast.makeText(this, "Run Test (المرحلة القادمة)", Toast.LENGTH_SHORT).show();
        });

        menuView.findViewById(R.id.menuSave).setOnClickListener(v -> {
            Toast.makeText(this, "Save (المرحلة القادمة)", Toast.LENGTH_SHORT).show();
        });

        menuView.findViewById(R.id.menuMore).setOnClickListener(v -> {
            Toast.makeText(this, "More (قريباً)", Toast.LENGTH_SHORT).show();
        });

        menuView.findViewById(R.id.menuExit).setOnClickListener(v -> {
            Toast.makeText(this, "إغلاق النافذة...", Toast.LENGTH_SHORT).show();
            stopSelf();
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Auto Service Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Auto Automation")
                .setContentText("النافذة العائمة تعمل بنجاح")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
        }
        if (menuView != null && windowManager != null && menuView.getWindowToken() != null) {
            try { windowManager.removeView(menuView); } catch (Exception ignored) {}
        }
    }
}
