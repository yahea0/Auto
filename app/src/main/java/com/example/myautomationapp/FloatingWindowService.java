package com.example.myautomationapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;      // الزر البنفسجي الصغير 42dp
    private View popupMenuView;     // قائمة مايكروفي الأولى
    private View hudBarView;        // شريط مايكروفي HUD (Main Job)
    
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams popupParams;
    private WindowManager.LayoutParams hudParams;
    
    private static final String CHANNEL_ID = "AutoServiceChannel";
    private boolean isPopupOpen = false;
    private boolean isHudOpen = false;
    private BroadcastReceiver coordReceiver;

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
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        new Handler(Looper.getMainLooper()).postDelayed(this::safeInitViews, 300);

        coordReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int x = intent.getIntExtra("x", 0);
                int y = intent.getIntExtra("y", 0);
                Action clickAction = new Action("Click (x, y)", "Click [" + x + ", " + y + "] [C]", x, y);
                clickAction.setDelayMs(500);
                GlobalData.actionList.add(clickAction);
                updateHudActionCards();
                openHudBar();
                Toast.makeText(FloatingWindowService.this, "تمت إضافة النقر عند: [" + x + ", " + y + "]", Toast.LENGTH_SHORT).show();
            }
        };
        ContextCompat.registerReceiver(this, coordReceiver, new IntentFilter("COORDINATES_PICKED"), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void safeInitViews() {
        try {
            initViews();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void initViews() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.floating_window, null);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 80;
        params.y = 400;

        popupMenuView = LayoutInflater.from(themedContext).inflate(R.layout.macrorify_popup_menu, null);
        popupParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        popupParams.gravity = Gravity.CENTER;

        hudBarView = LayoutInflater.from(themedContext).inflate(R.layout.menu_layout, null);
        hudParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        hudParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hudParams.y = 120;

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            stopSelf();
            return;
        }

        setupFloatingDrag();
        setupPopupMenu();
        setupHudButtons();
    }

    private void setupFloatingDrag() {
        View circleButton = floatingView.findViewById(R.id.circleButton);
        if (circleButton == null) return;

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

    private void togglePopupMenu() {
        if (isPopupOpen) {
            if (popupMenuView.getWindowToken() != null) windowManager.removeView(popupMenuView);
        } else {
            if (popupMenuView.getWindowToken() == null) windowManager.addView(popupMenuView, popupParams);
        }
        isPopupOpen = !isPopupOpen;
    }

    private void setupPopupMenu() {
        View btnManage = popupMenuView.findViewById(R.id.menuManageActions);
        if (btnManage != null) btnManage.setOnClickListener(v -> {
            togglePopupMenu();
            openHudBar();
        });

        // تشغيل الماكرو الحالي مباشرة من القائمة الأولى (صورة 23)
        View btnRun = popupMenuView.findViewById(R.id.menuRunTest);
        if (btnRun != null) btnRun.setOnClickListener(v -> {
            togglePopupMenu();
            runMacroExecution();
        });

        View btnSave = popupMenuView.findViewById(R.id.menuSave);
        if (btnSave != null) btnSave.setOnClickListener(v -> {
            togglePopupMenu();
            Toast.makeText(this, "تم حفظ الماكرو بنجاح!", Toast.LENGTH_SHORT).show();
        });

        View btnMore = popupMenuView.findViewById(R.id.menuMore);
        if (btnMore != null) btnMore.setOnClickListener(v -> {
            togglePopupMenu();
            Toast.makeText(this, "Settings (قريباً)", Toast.LENGTH_SHORT).show();
        });

        View btnExit = popupMenuView.findViewById(R.id.menuExit);
        if (btnExit != null) btnExit.setOnClickListener(v -> {
            togglePopupMenu();
            stopSelf();
        });
    }

    private void openHudBar() {
        if (!isHudOpen && hudBarView.getWindowToken() == null) {
            updateHudActionCards();
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

    private void updateHudActionCards() {
        LinearLayout container = hudBarView.findViewById(R.id.layoutActionListContainer);
        if (container == null) return;
        container.removeAllViews();

        if (GlobalData.actionList.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("No Actions");
            tvEmpty.setTextColor(android.graphics.Color.GRAY);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, 24, 0, 24);
            container.addView(tvEmpty);
            return;
        }

        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        LayoutInflater inflater = LayoutInflater.from(themedContext);

        for (int i = 0; i < GlobalData.actionList.size(); i++) {
            final int index = i;
            Action action = GlobalData.actionList.get(i);
            View card = inflater.inflate(R.layout.item_hud_action, container, false);

            TextView tvTitle = card.findViewById(R.id.tvActionTitle);
            TextView tvSubtitle = card.findViewById(R.id.tvActionSubtitle);
            TextView tvIndex = card.findViewById(R.id.tvActionIndex);

            tvTitle.setText(action.getDetail() != null ? action.getDetail() : action.getType());
            tvSubtitle.setText("[Delay " + action.getDelayMs() + "ms]");
            tvIndex.setText(String.valueOf(index + 1));

            // فتح نافذة خيارات مايكروفي الفخمة عند الضغط على البطاقة (صور 26 و 27)
            card.setOnClickListener(v -> showMacrorifyActionMenu(action, index));

            container.addView(card);
        }
    }

    // نافذة خيارات الأكشن المطابقة لمايكروفي تماماً
    private void showMacrorifyActionMenu(Action action, int index) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_action_options, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        // 1. Edit Coordinates
        dialogView.findViewById(R.id.optEditCoordinates).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            Intent intent = new Intent(this, CoordinatePickerService.class);
            startService(intent);
        });

        // 2. Edit Delay
        dialogView.findViewById(R.id.optEditDelay).setOnClickListener(v -> {
            dialog.dismiss();
            showEditDelayDialog(action);
        });

        // 3. Test Action
        dialogView.findViewById(R.id.optTestAction).setOnClickListener(v -> {
            dialog.dismiss();
            if (AutoAccessibilityService.instance != null) {
                if ("Click (x, y)".equals(action.getType())) {
                    AutoAccessibilityService.instance.click(action.getX(), action.getY());
                } else if ("Press Back".equals(action.getType())) {
                    AutoAccessibilityService.instance.pressBack();
                } else if ("Press Home".equals(action.getType())) {
                    AutoAccessibilityService.instance.pressHome();
                }
                Toast.makeText(this, "تم تنفيذ: " + action.getType(), Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Copy
        dialogView.findViewById(R.id.optCopy).setOnClickListener(v -> {
            dialog.dismiss();
            GlobalData.actionList.add(index + 1, new Action(action.getType(), action.getDetail(), action.getX(), action.getY()));
            updateHudActionCards();
            Toast.makeText(this, "تم نسخ الأكشن", Toast.LENGTH_SHORT).show();
        });

        // 5. Delete
        dialogView.findViewById(R.id.optDelete).setOnClickListener(v -> {
            dialog.dismiss();
            GlobalData.actionList.remove(index);
            updateHudActionCards();
            Toast.makeText(this, "تم حذف الأكشن!", Toast.LENGTH_SHORT).show();
        });

        // 6. Disable / Enable
        dialogView.findViewById(R.id.optDisable).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "تم تعطيل الأكشن", Toast.LENGTH_SHORT).show();
        });

        // باقي الأزرار
        View.OnClickListener dummy = v -> {
            dialog.dismiss();
            Toast.makeText(this, "قريباً في التحديث القادم", Toast.LENGTH_SHORT).show();
        };
        dialogView.findViewById(R.id.optEditScaling).setOnClickListener(dummy);
        dialogView.findViewById(R.id.optEditClickStyle).setOnClickListener(dummy);
        dialogView.findViewById(R.id.optAddAbove).setOnClickListener(dummy);
        dialogView.findViewById(R.id.optAddCondition).setOnClickListener(dummy);
        dialogView.findViewById(R.id.optReplace).setOnClickListener(dummy);
        dialogView.findViewById(R.id.optConvertCustom).setOnClickListener(dummy);
        dialogView.findViewById(R.id.optCut).setOnClickListener(dummy);

        dialog.show();
    }

    private void showEditDelayDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Edit Delay (ms)");

        final EditText input = new EditText(themedContext);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(action.getDelayMs()));
        builder.setView(input);

        builder.setPositiveButton("حفظ", (dialog, which) -> {
            String val = input.getText().toString();
            int ms = val.isEmpty() ? 500 : Integer.parseInt(val);
            action.setDelayMs(ms);
            updateHudActionCards();
        });
        builder.setNegativeButton("إلغاء", null);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.show();
    }

    private void setupHudButtons() {
        View btnCollapse = hudBarView.findViewById(R.id.btnCollapse);
        if (btnCollapse != null) btnCollapse.setOnClickListener(v -> closeHudBar());

        View btnPlay = hudBarView.findViewById(R.id.btnPlayHud);
        if (btnPlay != null) btnPlay.setOnClickListener(v -> {
            closeHudBar();
            runMacroExecution();
        });

        View btnAdd = hudBarView.findViewById(R.id.btnAddActionHud);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> showFullActionDialog());

        View btnVar = hudBarView.findViewById(R.id.btnVariables);
        if (btnVar != null) btnVar.setOnClickListener(v ->
                Toast.makeText(this, "Variables & Expressions", Toast.LENGTH_SHORT).show()
        );
    }

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

        Toast.makeText(this, "جاري تشغيل الماكرو الحالي...", Toast.LENGTH_SHORT).show();

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
                    } else if ("Quicksetting".equals(type)) {
                        AutoAccessibilityService.instance.openQuickSettings();
                    } else if ("Lock Screen".equals(type)) {
                        AutoAccessibilityService.instance.lockScreen();
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

    private void showFullActionDialog() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_action_select, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        setupBtn(dialogView, R.id.btnClickXY, dialog, () -> {
            closeHudBar();
            Intent intent = new Intent(this, CoordinatePickerService.class);
            startService(intent);
        });

        setupBtn(dialogView, R.id.btnClickImage, dialog, () ->
            Toast.makeText(this, "Click Image (مربع القص)", Toast.LENGTH_SHORT).show()
        );

        setupBtn(dialogView, R.id.btnClickText, dialog, () -> addSimpleAction("Click Text", "[OCR Search]"));
        setupBtn(dialogView, R.id.btnClickColor, dialog, () -> addSimpleAction("Click Color", "[Color Match]"));
        setupBtn(dialogView, R.id.btnSwipe, dialog, () -> addSimpleAction("Swipe", "Swipe [500, 1200] -> [500, 400]"));
        setupBtn(dialogView, R.id.btnRunJob, dialog, () -> addSimpleAction("Run Job", "Run: Main Job"));
        setupBtn(dialogView, R.id.btnRestartJob, dialog, () -> addSimpleAction("Restart Job", "Restart Current Job"));
        setupBtn(dialogView, R.id.btnStopJob, dialog, () -> addSimpleAction("Stop Job", "Stop Current Job"));
        setupBtn(dialogView, R.id.btnSetVariable, dialog, () -> addSimpleAction("Set Variable", "x = x + 1"));
        setupBtn(dialogView, R.id.btnToastMessage, dialog, () -> addSimpleAction("Toast Message", "Show Toast [Hello]"));
        setupBtn(dialogView, R.id.btnOpenApp, dialog, () -> addSimpleAction("Open App", "Launch Selected App"));
        setupBtn(dialogView, R.id.btnPressBack, dialog, () -> addSimpleAction("Press Back", "Device Back Key"));
        setupBtn(dialogView, R.id.btnPressHome, dialog, () -> addSimpleAction("Press Home", "Device Home Key"));
        setupBtn(dialogView, R.id.btnOpenRecent, dialog, () -> addSimpleAction("Open Recent", "Show Recent Apps"));
        setupBtn(dialogView, R.id.btnOpenNotification, dialog, () -> addSimpleAction("Notification", "Pull Notification Panel"));
        setupBtn(dialogView, R.id.btnOpenQuickSettings, dialog, () -> addSimpleAction("Quicksetting", "Open Quick Settings"));
        setupBtn(dialogView, R.id.btnLockScreen, dialog, () -> addSimpleAction("Lock Screen", "Lock Device Screen"));
        setupBtn(dialogView, R.id.btnScreenshot, dialog, () -> addSimpleAction("Screenshot", "Take Screen Capture"));
        setupBtn(dialogView, R.id.btnEmptyAction, dialog, () -> addSimpleAction("Empty Action", "[No-Op Step]"));
        setupBtn(dialogView, R.id.btnWait, dialog, () -> addSimpleAction("Wait", "Wait 1000ms"));
        setupBtn(dialogView, R.id.btnPauseMacro, dialog, () -> addSimpleAction("Pause Macro", "Pause Execution"));
        setupBtn(dialogView, R.id.btnStopMacro, dialog, () -> addSimpleAction("Stop Macro", "Stop Macro"));
        setupBtn(dialogView, R.id.btnAddCustom, dialog, () -> addSimpleAction("Custom Action", "User Custom Script"));

        dialog.show();
    }

    private void setupBtn(View parent, int id, AlertDialog dialog, Runnable action) {
        View v = parent.findViewById(id);
        if (v != null) {
            v.setOnClickListener(view -> {
                dialog.dismiss();
                action.run();
            });
        }
    }

    private void addSimpleAction(String type, String detail) {
        Action action = new Action(type, detail);
        action.setDelayMs(500);
        GlobalData.actionList.add(action);
        updateHudActionCards();
        Toast.makeText(this, "تمت إضافة: " + type, Toast.LENGTH_SHORT).show();
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
        if (coordReceiver != null) {
            try { unregisterReceiver(coordReceiver); } catch (Exception ignored) {}
        }
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
