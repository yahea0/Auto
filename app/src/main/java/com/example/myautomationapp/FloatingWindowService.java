package com.example.myautomationapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private boolean isMinimized = false;
    private static final String CHANNEL_ID = "AutoServiceChannel";

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
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, buildNotification());
        }
        
        // الحيلة رقم 1: نفحص الصلاحية بطريقة حقيقية قبل ما نضيف النافذة
        if (!canDrawOverlayHack(this)) {
            Toast.makeText(this, "الصلاحية مرفوضة من النظام. يرجى تفعيل 'الظهور فوق التطبيقات' يدوياً.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // الحيلة رقم 3: ننتظر ثانية كاملة قبل ما نضيف النافذة (عشان ريلمي)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            addOverlayView();
        }, 1000);
    }
    
    private void addOverlayView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // الحيلة رقم 2: نضيف أعلام جديدة عشان نضمن إن النافذة تشتغل
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
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

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ حرج: النظام رفض إضافة النافذة. حاول إعادة تشغيل التطبيق.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        // جعل النافذة قابلة للسحب
        TextView header = floatingView.findViewById(R.id.headerTitle);
        header.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        ImageButton btnMinimize = floatingView.findViewById(R.id.btnMinimize);
        btnMinimize.setOnClickListener(v -> {
            if (isMinimized) {
                floatingView.findViewById(R.id.expandedLayout).setVisibility(View.VISIBLE);
                btnMinimize.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            } else {
                floatingView.findViewById(R.id.expandedLayout).setVisibility(View.GONE);
                btnMinimize.setImageResource(android.R.drawable.ic_menu_add);
            }
            isMinimized = !isMinimized;
        });
        
        floatingView.findViewById(R.id.btnAddAction).setOnClickListener(v -> {
            Toast.makeText(this, "قائمة الأكشنات (قريباً)", Toast.LENGTH_SHORT).show();
        });
    }

    // هذه هي الحيلة: نجرب نضيف نافذة غير مرئية عشان نتأكد إن الصلاحية شغالة
    private boolean canDrawOverlayHack(Context context) {
        try {
            WindowManager mgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            View viewToAdd = new View(context);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    0, 0,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
            viewToAdd.setLayoutParams(params);
            mgr.addView(viewToAdd, params);
            mgr.removeView(viewToAdd);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
