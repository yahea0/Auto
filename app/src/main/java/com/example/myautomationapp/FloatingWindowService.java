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
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;      // الزر الذهبي الصغير
    private View hudBarView;        // شريط مايكروفي HUD العائم
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams hudParams;
    private static final String CHANNEL_ID = "AutoServiceChannel";
    private boolean isHudOpen = false;

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
        new Handler(Looper.getMainLooper()).postDelayed(this::initViews, 300);
    }

    private void initViews() {
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        // 1. الزر الدائري الصغير
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        // 2. شريط مايكروفي HUD العريض
        hudBarView = LayoutInflater.from(this).inflate(R.layout.menu_layout, null);
        hudParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        hudParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hudParams.y = 150;

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
            return;
        }

        setupFloatingDrag();
        setupHudButtons();
    }

    private void setupFloatingDrag() {
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
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            toggleHudBar();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleHudBar() {
        if (isHudOpen) {
            if (hudBarView.getWindowToken() != null) {
                windowManager.removeView(hudBarView);
            }
        } else {
            if (hudBarView.getWindowToken() == null) {
                TextView tvStatus = hudBarView.findViewById(R.id.tvHudStatus);
                if (tvStatus != null) {
                    tvStatus.setText(GlobalData.actionList.size() + " Actions Ready");
                }
                windowManager.addView(hudBarView, hudParams);
            }
        }
        isHudOpen = !isHudOpen;
    }

    private void setupHudButtons() {
        // زر التصغير ⤢
        hudBarView.findViewById(R.id.btnCollapse).setOnClickListener(v -> toggleHudBar());

        // زر إضافة أكشن جديد + (فتح شاشة الأكشنات أو الإضافة)
        hudBarView.findViewById(R.id.btnAddActionHud).setOnClickListener(v -> {
            toggleHudBar();
            Intent intent = new Intent(this, ActionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        // زر التشغيل السريع ▶ فوق أي لعبة أو تطبيق
        hudBarView.findViewById(R.id.btnPlayHud).setOnClickListener(v -> {
            if (GlobalData.actionList.isEmpty()) {
                Toast.makeText(this, "لا يوجد أكشنات للتشغيل!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AutoAccessibilityService.isRunning()) {
                Toast.makeText(this, "فعّل خدمة إمكانية الوصول أولاً!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }

            // تصغير الشريط للبدء بمراقبة اللعبة
            toggleHudBar();
            Toast.makeText(this, "بدء تنفيذ الماكرو...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                for (Action action : GlobalData.actionList) {
                    if (AutoAccessibilityService.instance != null) {
                        if ("Click (x, y)".equals(action.getType())) {
                            AutoAccessibilityService.instance.click(action.getX(), action.getY());
                        } else if ("Press Back".equals(action.getType())) {
                            AutoAccessibilityService.instance.pressBack();
                        } else if ("Swipe".equals(action.getType())) {
                            AutoAccessibilityService.instance.swipe(500, 1200, 500, 400, 400);
                        }
                    }
                    try {
                        Thread.sleep(action.getDelayMs());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(FloatingWindowService.this, "تم تنفيذ الماكرو بنجاح!", Toast.LENGTH_SHORT).show()
                );
            }).start();
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
                .setContentTitle("Auto Macrorify Engine")
                .setContentText("الشريط العائم نشط")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
        }
        if (hudBarView != null && windowManager != null && hudBarView.getWindowToken() != null) {
            try { windowManager.removeView(hudBarView); } catch (Exception ignored) {}
        }
    }
}
