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
    private View popupMenuView;     // قائمة مايكروفي المنبثقة الأولى (Manage, Run, Save, More, Exit)
    private View hudBarView;        // شريط مايكروفي HUD العلوي (Main Job)
    
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams popupParams;
    private WindowManager.LayoutParams hudParams;
    
    private static final String CHANNEL_ID = "AutoServiceChannel";
    private boolean isPopupOpen = false;
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

        // 1. الزر العائم الصغير
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 80;
        params.y = 400;

        // 2. قائمة مايكروفي المنبثقة الأولى
        popupMenuView = LayoutInflater.from(this).inflate(R.layout.macrorify_popup_menu, null);
        popupParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        popupParams.gravity = Gravity.CENTER;

        // 3. شريط مايكروفي HUD العلوي (Main Job)
        hudBarView = LayoutInflater.from(this).inflate(R.layout.menu_layout, null);
        hudParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        hudParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hudParams.y = 120;

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
            return;
        }

        setupFloatingDrag();
        setupPopupMenu();
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
                            togglePopupMenu();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // 1. فتح وإغلاق قائمة مايكروفي المنبثقة الأولى
    private void togglePopupMenu() {
        if (isPopupOpen) {
            if (popupMenuView.getWindowToken() != null) {
                windowManager.removeView(popupMenuView);
            }
        } else {
            if (popupMenuView.getWindowToken() == null) {
                windowManager.addView(popupMenuView, popupParams);
            }
        }
        isPopupOpen = !isPopupOpen;
    }

    private void setupPopupMenu() {
        // Manage Actions: يغلق القائمة المنبثقة ويفتح شريط مايكروفي HUD العلوي!
        popupMenuView.findViewById(R.id.menuManageActions).setOnClickListener(v -> {
            togglePopupMenu();
            openHudBar();
        });

        // Run Test
        popupMenuView.findViewById(R.id.menuRunTest).setOnClickListener(v -> {
            togglePopupMenu();
            runMacroExecution();
        });

        // Save
        popupMenuView.findViewById(R.id.menuSave).setOnClickListener(v -> {
            togglePopupMenu();
            Toast.makeText(this, "تم حفظ الماكرو بنجاح!", Toast.LENGTH_SHORT).show();
        });

        // More
        popupMenuView.findViewById(R.id.menuMore).setOnClickListener(v -> {
            togglePopupMenu();
            Toast.makeText(this, "Settings & Tools (قريباً)", Toast.LENGTH_SHORT).show();
        });

        // Exit
        popupMenuView.findViewById(R.id.menuExit).setOnClickListener(v -> {
            togglePopupMenu();
            stopSelf();
        });
    }

    // 2. التحكم في شريط Main Job العلوي
    private void openHudBar() {
        if (!isHudOpen && hudBarView.getWindowToken() == null) {
            updateHudStatus();
            windowManager.addView(hudBarView, hudParams);
            isHudOpen = true;
        }
    }

    private void closeHudBar() {
        if (isHudOpen && hudBarView.getWindowToken() != null) {
            windowManager.removeView(hudBarView);
            isHudOpen = false;
        }
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
        // زر التصغير ⤢
        hudBarView.findViewById(R.id.btnCollapse).setOnClickListener(v -> closeHudBar());

        // زر التشغيل ▶
        hudBarView.findViewById(R.id.btnPlayHud).setOnClickListener(v -> {
            closeHudBar();
            runMacroExecution();
        });

        // زر إضافة أكشن + (يفتح قائمة الأكشنات الكاملة المطابقة لمايكروفي)
        hudBarView.findViewById(R.id.btnAddActionHud).setOnClickListener(v -> showFullActionDialog());

        // زر المتغيرات (x)
        hudBarView.findViewById(R.id.btnVariables).setOnClickListener(v ->
                Toast.makeText(this, "Variables & Expressions", Toast.LENGTH_SHORT).show()
        );
    }

    // تشغيل الأكشنات بالترتيب
    private void runMacroExecution() {
        if (GlobalData.actionList.isEmpty()) {
            Toast.makeText(this, "لا يوجد أكشنات للتشغيل!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AutoAccessibilityService.isRunning()) {
            Toast.makeText(this, "يرجى تفعيل خدمة إمكانية الوصول أولاً!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }

        Toast.makeText(this, "بدء تشغيل الماكرو...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            for (Action action : GlobalData.actionList) {
                if (AutoAccessibilityService.instance != null) {
                    String type = action.getType();
                    if ("Click (x, y)".equals(type)) {
                        AutoAccessibilityService.instance.click(action.getX(), action.getY());
                    } else if ("Swipe".equals(type)) {
                        AutoAccessibilityService.instance.swipe(500, 1200, 500, 400, 400);
                    } else if ("Press Back".equals(type)) {
                        AutoAccessibilityService.instance.pressBack();
                    } else if ("Press Home".equals(type)) {
                        AutoAccessibilityService.instance.pressHome();
                    } else if ("Open Recent".equals(type)) {
                        AutoAccessibilityService.instance.openRecents();
                    } else if ("Notification".equals(type)) {
                        AutoAccessibilityService.instance.openNotifications();
                    } else if ("Screenshot".equals(type)) {
                        AutoAccessibilityService.instance.takeScreenshot();
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
    }

    // عرض قائمة الأكشنات المتكاملة والمطابقة لصور مايكروفي
    private void showFullActionDialog() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_action_select, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        // Gesture: Click XY
        dialogView.findViewById(R.id.btnClickXY).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            Intent intent = new Intent(this, CoordinatePickerService.class);
            startService(intent);
        });

        // Gesture: Click Image
        dialogView.findViewById(R.id.btnClickImage).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "مربع قص الصور (الخطوة التالية فوراً)", Toast.LENGTH_SHORT).show();
        });

        // Gesture: Click Text
        dialogView.findViewById(R.id.btnClickText).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Click Text (OCR)", Toast.LENGTH_SHORT).show();
        });

        // Gesture: Swipe
        dialogView.findViewById(R.id.btnSwipe).setOnClickListener(v -> {
            dialog.dismiss();
            Action swipe = new Action("Swipe", "سحب للأعلى (500,1200) إلى (500,400)");
            swipe.setDelayMs(600);
            GlobalData.actionList.add(swipe);
            updateHudStatus();
        });

        // Device: Press Back
        dialogView.findViewById(R.id.btnPressBack).setOnClickListener(v -> {
            dialog.dismiss();
            Action back = new Action("Press Back", "زر رجوع الجهاز");
            back.setDelayMs(500);
            GlobalData.actionList.add(back);
            updateHudStatus();
        });

        // Device: Press Home
        dialogView.findViewById(R.id.btnPressHome).setOnClickListener(v -> {
            dialog.dismiss();
            Action home = new Action("Press Home", "زر الشاشة الرئيسية");
            home.setDelayMs(500);
            GlobalData.actionList.add(home);
            updateHudStatus();
        });

        // Device: Open Recent
        dialogView.findViewById(R.id.btnOpenRecent).setOnClickListener(v -> {
            dialog.dismiss();
            Action recent = new Action("Open Recent", "التطبيقات الحديثة");
            recent.setDelayMs(500);
            GlobalData.actionList.add(recent);
            updateHudStatus();
        });

        // Device: Notification
        dialogView.findViewById(R.id.btnOpenNotification).setOnClickListener(v -> {
            dialog.dismiss();
            Action notif = new Action("Notification", "سحب شريط الإشعارات");
            notif.setDelayMs(500);
            GlobalData.actionList.add(notif);
            updateHudStatus();
        });

        // Device: Screenshot
        dialogView.findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            dialog.dismiss();
            Action shot = new Action("Screenshot", "التقاط شاشة");
            shot.setDelayMs(600);
            GlobalData.actionList.add(shot);
            updateHudStatus();
        });

        // Device: Toast Message
        dialogView.findViewById(R.id.btnToastMessage).setOnClickListener(v -> {
            dialog.dismiss();
            Action toastAct = new Action("Toast Message", "إظهار رسالة منبثقة");
            toastAct.setDelayMs(400);
            GlobalData.actionList.add(toastAct);
            updateHudStatus();
        });

        // Macro: Wait
        dialogView.findViewById(R.id.btnWait).setOnClickListener(v -> {
            dialog.dismiss();
            Action waitAct = new Action("Wait", "انتظار 1 ثانية");
            waitAct.setDelayMs(1000);
            GlobalData.actionList.add(waitAct);
            updateHudStatus();
        });

        // Macro: Stop Macro
        dialogView.findViewById(R.id.btnStopMacro).setOnClickListener(v -> {
            dialog.dismiss();
            Action stopAct = new Action("Stop Macro", "إيقاف التشغيل");
            GlobalData.actionList.add(stopAct);
            updateHudStatus();
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
                .setContentTitle("Macrorify Engine")
                .setContentText("الخدمة العائمة نشطة")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
        }
        if (popupMenuView != null && windowManager != null && popupMenuView.getWindowToken() != null) {
            try { windowManager.removeView(popupMenuView); } catch (Exception ignored) {}
        }
        if (hudBarView != null && windowManager != null && hudBarView.getWindowToken() != null) {
            try { windowManager.removeView(hudBarView); } catch (Exception ignored) {}
        }
    }
}
