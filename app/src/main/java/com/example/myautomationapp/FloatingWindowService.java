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
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private static final String CHANNEL_ID = "AutoServiceChannel";
    private LinearLayout menuLayout;

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
                ServiceCompat.startForeground(this, 1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                ServiceCompat.startForeground(this, 1, buildNotification(), 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        new Handler(Looper.getMainLooper()).postDelayed(this::addOverlayView, 1000);
    }

    private void addOverlayView() {
        try {
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
            menuLayout = floatingView.findViewById(R.id.menuLayout);

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    flags,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 100;
            params.y = 200;

            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في إضافة النافذة: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        // منطق السحب والضغط على الدائرة
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
                        
                        // إذا تحرك الإصبع أكثر من 10 بكسل، نعتبرها سحبة
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                        }
                        
                        if (isDragging) {
                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;

                            // تقييد النافذة داخل حدود الشاشة
                            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                            int screenWidth = displayMetrics.widthPixels;
                            int screenHeight = displayMetrics.heightPixels;
                            int viewWidth = floatingView.getWidth();
                            int viewHeight = floatingView.getHeight();

                            if (viewWidth > 0 && viewHeight > 0) {
                                if (params.x < 0) params.x = 0;
                                if (params.x > screenWidth - viewWidth) params.x = screenWidth - viewWidth;
                                if (params.y < 0) params.y = 0;
                                if (params.y > screenHeight - viewHeight) params.y = screenHeight - viewHeight;
                            }
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        // إذا ما تحرك، نعتبرها ضغطة عادية
                        if (!isDragging) {
                            if (menuLayout.getVisibility() == View.VISIBLE) {
                                menuLayout.setVisibility(View.GONE);
                            } else {
                                menuLayout.setVisibility(View.VISIBLE);
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        // أزرار القائمة
        floatingView.findViewById(R.id.menuManage).setOnClickListener(v -> {
            Toast.makeText(this, "Manage Actions (قريباً)", Toast.LENGTH_SHORT).show();
        });
        
        floatingView.findViewById(R.id.menuRunTest).setOnClickListener(v -> {
            Toast.makeText(this, "Run Test (قريباً)", Toast.LENGTH_SHORT).show();
        });
        
        floatingView.findViewById(R.id.menuSave).setOnClickListener(v -> {
            Toast.makeText(this, "Save (قريباً)", Toast.LENGTH_SHORT).show();
        });
        
        floatingView.findViewById(R.id.menuMore).setOnClickListener(v -> {
            Toast.makeText(this, "More (قريباً)", Toast.LENGTH_SHORT).show();
        });
        
        floatingView.findViewById(R.id.menuExit).setOnClickListener(v -> {
            Toast.makeText(this, "Exiting...", Toast.LENGTH_SHORT).show();
            stopSelf();
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Auto Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Auto Automation")
                .setContentText("النافذة العائمة تعمل")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
