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
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;      // الزر البنفسجي الصغير 42dp
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

        // 1. تهيئة الزر العائم الصغير
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 80;
        params.y = 400;

        // 2. تهيئة شريط Macrorify HUD
        hudBarView = LayoutInflater.from(this).inflate(R.layout.menu_layout, null);
        hudParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        hudParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hudParams.y = 200;

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
                            int targetX = initialX + (int) deltaX;
                            int targetY = initialY + (int) deltaY;

                            // حماية حدود شاشة الهاتف (Realme GT Master Edition) لمنع خروج الزر
                            DisplayMetrics dm = getResources().getDisplayMetrics();
                            int viewWidth = floatingView.getWidth() > 0 ? floatingView.getWidth() : 110;
                            int viewHeight = floatingView.getHeight() > 0 ? floatingView.getHeight() : 110;

                            if (targetX < 0) targetX = 0;
                            if (targetX > dm.widthPixels - viewWidth) targetX = dm.widthPixels - viewWidth;
                            if (targetY < 80) targetY = 80;
                            if (targetY > dm.heightPixels - viewHeight - 80) targetY = dm.heightPixels - viewHeight - 80;

                            params.x = targetX;
                            params.y = targetY;
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

    public void toggleHudBar() {
        if (isHudOpen) {
            if (hudBarView.getWindowToken() != null) {
                windowManager.removeView(hudBarView);
            }
        } else {
            if (hudBarView.getWindowToken() == null) {
                updateHudStatus();
                windowManager.addView(hudBarView, hudParams);
            }
        }
        isHudOpen = !isHudOpen;
    }

    private void updateHudStatus() {
        TextView tvStatus = hudBarView.findViewById(R.id.tvHudStatus);
        if (tvStatus != null) {
            if (GlobalData.actionList.isEmpty()) {
                tvStatus.setText("No Actions");
            } else {
                tvStatus.setText(GlobalData.actionList.size() + " Actions Ready");
            }
        }
    }

    private void setupHudButtons() {
        // 1. زر التصغير ⤢ للعودة للزر العائم فوراً
        hudBarView.findViewById(R.id.btnCollapse).setOnClickListener(v -> toggleHudBar());

        // 2. زر التشغيل الفوري ▶ في نفس المكان بدون فتح أي شاشة
        hudBarView.findViewById(R.id.btnPlayHud).setOnClickListener(v -> {
            if (GlobalData.actionList.isEmpty()) {
                Toast.makeText(this, "لا يوجد أكشنات مضافة!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AutoAccessibilityService.isRunning()) {
                Toast.makeText(this, "يرجى تفعيل خدمة إمكانية الوصول أولاً!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }

            // تصغير الشريط فورياً لمراقبة تنفيذ الأكشنات على شاشة الهاتف/اللعبة
            toggleHudBar();
            Toast.makeText(this, "جاري تشغيل الماكرو...", Toast.LENGTH_SHORT).show();

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
                        Toast.makeText(FloatingWindowService.this, "اكتمل تشغيل الماكرو بنجاح!", Toast.LENGTH_SHORT).show()
                );
            }).start();
        });

        // 3. زر الإضافة + لإضافة أكشنات من الشريط العائم مباشرة
        hudBarView.findViewById(R.id.btnAddActionHud).setOnClickListener(v -> showAddActionDialog());

        // 4. زر المتغيرات (x)
        hudBarView.findViewById(R.id.btnVariables).setOnClickListener(v ->
                Toast.makeText(this, "Variables & Expressions (قريباً)", Toast.LENGTH_SHORT).show()
        );

        // 5. قائمة الوظيفة Main Job
        hudBarView.findViewById(R.id.layoutJobDropdown).setOnClickListener(v ->
                Toast.makeText(this, "Job Selector (قريباً)", Toast.LENGTH_SHORT).show()
        );
    }

    private void showAddActionDialog() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_action_select, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        dialogView.findViewById(R.id.btnClickXY).setOnClickListener(v -> {
            dialog.dismiss();
            toggleHudBar();
            Intent intent = new Intent(this, CoordinatePickerService.class);
            startService(intent);
        });

        dialogView.findViewById(R.id.btnClickImage).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Click Image (الخطوة التالية لمربع القص المطاطي)", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnSwipe).setOnClickListener(v -> {
            dialog.dismiss();
            Action swipe = new Action("Swipe", "سحب للأعلى (500,1200) إلى (500,400)");
            swipe.setDelayMs(600);
            GlobalData.actionList.add(swipe);
            updateHudStatus();
            Toast.makeText(this, "تمت إضافة أمر السحب", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnWait).setOnClickListener(v -> {
            dialog.dismiss();
            Action waitAction = new Action("Wait", "انتظار 1 ثانية");
            waitAction.setDelayMs(1000);
            GlobalData.actionList.add(waitAction);
            updateHudStatus();
            Toast.makeText(this, "تمت إضافة انتظار 1 ثانية", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnPressBack).setOnClickListener(v -> {
            dialog.dismiss();
            Action backAction = new Action("Press Back", "الضغط على زر رجوع الجهاز");
            backAction.setDelayMs(500);
            GlobalData.actionList.add(backAction);
            updateHudStatus();
            Toast.makeText(this, "تمت إضافة أمر الرجوع", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnOpenApp).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Open App (قريباً)", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
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
                .setContentTitle("Macrorify HUD Engine")
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
