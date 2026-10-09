package com.example.myautomationapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.io.File;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingBadgeView;
    private View hudMenuView;
    private View floatingStopView;
    private WindowManager.LayoutParams badgeParams;
    private WindowManager.LayoutParams hudParams;
    private WindowManager.LayoutParams stopParams;

    private LinearLayout layoutActionListContainer;
    private TextView tvHudStatus;
    private BroadcastReceiver customRegionReceiver;
    private Action targetEditingAction;
    private TextView tvDialogCustomRegionCoords;

    private volatile boolean isRunningMacro = false;
    private Thread macroThread;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("resultCode") && intent.hasExtra("data")) {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent data = intent.getParcelableExtra("data");
            ScreenCaptureManager.getInstance().init(this, resultCode, data);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceNotification();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                  | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        // 1. الأيقونة العائمة المصغرة (Floating Badge)
        floatingBadgeView = LayoutInflater.from(themedContext).inflate(R.layout.floating_window, null);
        badgeParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        badgeParams.gravity = Gravity.TOP | Gravity.START;
        badgeParams.x = 20;
        badgeParams.y = dm.heightPixels / 3;

        // 2. قائمة لوحة التحكم العائمة (HUD Menu)
        hudMenuView = LayoutInflater.from(themedContext).inflate(R.layout.menu_layout, null);
        hudParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        hudParams.gravity = Gravity.TOP | Gravity.START;
        hudParams.x = (dm.widthPixels / 2) - (int)(175 * dm.density);
        hudParams.y = 80;

        // 3. زر الإيقاف العائم (Stop Button)
        floatingStopView = LayoutInflater.from(themedContext).inflate(R.layout.floating_stop_button, null);
        stopParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        stopParams.gravity = Gravity.TOP | Gravity.END;
        stopParams.x = 20;
        stopParams.y = 100;

        layoutActionListContainer = hudMenuView.findViewById(R.id.layoutActionListContainer);
        tvHudStatus = hudMenuView.findViewById(R.id.tvHudStatus);

        try {
            windowManager.addView(floatingBadgeView, badgeParams);
            windowManager.addView(hudMenuView, hudParams);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
            return;
        }

        floatingBadgeView.setVisibility(View.GONE);
        hudMenuView.setVisibility(View.VISIBLE);

        setupBadgeTouch();
        setupHudTouch();
        setupHudButtons();
        setupStopButton();
        refreshHudActionCards();
        registerCustomRegionReceiver();
    }

    private void startForegroundServiceNotification() {
        String channelId = "AutoAutomationServiceChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Automation Overlay", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Auto")
                .setContentText("خدمة الأتمتة والنافذة العائمة تعمل...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();

        startForeground(101, notification);
    }

    private void registerCustomRegionReceiver() {
        customRegionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || targetEditingAction == null) return;
                int rx = intent.getIntExtra("region_x", 0);
                int ry = intent.getIntExtra("region_y", 0);
                int rw = intent.getIntExtra("region_w", 0);
                int rh = intent.getIntExtra("region_h", 0);

                targetEditingAction.setCustomRegion(rx, ry, rw, rh);
                targetEditingAction.setDetectLocationMode("CUSTOM");

                if (tvDialogCustomRegionCoords != null) {
                    tvDialogCustomRegionCoords.setText(rx + ", " + ry + ", " + rw + ", " + rh);
                }

                refreshHudActionCards();
                Toast.makeText(context, "تم تحديد المنطقة: [" + rx + ", " + ry + ", " + rw + ", " + rh + "]", Toast.LENGTH_SHORT).show();
            }
        };

        ContextCompat.registerReceiver(
                this,
                customRegionReceiver,
                new IntentFilter("CUSTOM_REGION_SELECTED"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private void setupBadgeTouch() {
        floatingBadgeView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float touchX, touchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = badgeParams.x; initialY = badgeParams.y;
                        touchX = event.getRawX(); touchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        badgeParams.x = initialX + (int)(event.getRawX() - touchX);
                        badgeParams.y = initialY + (int)(event.getRawY() - touchY);
                        windowManager.updateViewLayout(floatingBadgeView, badgeParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - touchStartTime < 200) {
                            floatingBadgeView.setVisibility(View.GONE);
                            hudMenuView.setVisibility(View.VISIBLE);
                            refreshHudActionCards();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void setupHudTouch() {
        View titleBar = hudMenuView.findViewById(R.id.layoutJobDropdown);
        titleBar.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float touchX, touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = hudParams.x; initialY = hudParams.y;
                        touchX = event.getRawX(); touchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        hudParams.x = initialX + (int)(event.getRawX() - touchX);
                        hudParams.y = initialY + (int)(event.getRawY() - touchY);
                        windowManager.updateViewLayout(hudMenuView, hudParams);
                        return true;
                }
                return false;
            }
        });
    }

    private void setupHudButtons() {
        ImageButton btnCollapse = hudMenuView.findViewById(R.id.btnCollapse);
        btnCollapse.setOnClickListener(v -> {
            hudMenuView.setVisibility(View.GONE);
            floatingBadgeView.setVisibility(View.VISIBLE);
        });

        ImageButton btnPlayHud = hudMenuView.findViewById(R.id.btnPlayHud);
        btnPlayHud.setOnClickListener(v -> startAutomationExecution());

        ImageButton btnAddActionHud = hudMenuView.findViewById(R.id.btnAddActionHud);
        btnAddActionHud.setOnClickListener(v -> {
            Intent intent = new Intent(FloatingWindowService.this, ActionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
    }

    private void setupStopButton() {
        floatingStopView.findViewById(R.id.cardStopButton).setOnClickListener(v -> stopAutomationExecution());
    }

    private void refreshHudActionCards() {
        if (layoutActionListContainer == null) return;
        layoutActionListContainer.removeAllViews();

        if (GlobalData.actionList == null || GlobalData.actionList.isEmpty()) {
            if (tvHudStatus != null) {
                tvHudStatus.setVisibility(View.VISIBLE);
                layoutActionListContainer.addView(tvHudStatus);
            }
            return;
        }

        if (tvHudStatus != null) tvHudStatus.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp));

        for (int i = 0; i < GlobalData.actionList.size(); i++) {
            final int index = i;
            final Action action = GlobalData.actionList.get(i);
            View card = inflater.inflate(R.layout.item_hud_action, layoutActionListContainer, false);

            TextView tvActionTitle = card.findViewById(R.id.tvActionTitle);
            TextView tvActionSubtitle = card.findViewById(R.id.tvActionSubtitle);
            TextView tvActionIndex = card.findViewById(R.id.tvActionIndex);
            ImageView ivActionIcon = card.findViewById(R.id.ivActionIcon);

            tvActionIndex.setText(String.valueOf(index + 1));
            tvActionTitle.setText(action.getType() + " [" + action.getImageName() + "]");
            tvActionSubtitle.setText("[Delay " + action.getDelayBeforeMs() + "ms/" + action.getDelayAfterMs() + "ms]");

            LinearLayout layoutConditionBranch = card.findViewById(R.id.layoutConditionBranch);
            TextView tvConditionTitle = card.findViewById(R.id.tvConditionTitle);
            TextView tvConditionSubtitle = card.findViewById(R.id.tvConditionSubtitle);
            ImageView ivConditionThumb = card.findViewById(R.id.ivConditionThumb);

            // عرض فرع شرط الصورة والموقع (مثل صورتك تماماً)
            layoutConditionBranch.setVisibility(View.VISIBLE);
            tvConditionTitle.setText("Image [" + action.getImageName() + "] [" + (action.isNotAppear() ? "Not Appear" : "Appear") + "] [" + action.getSimilarity() + "%]");

            String modeDesc = "[Captured Location]";
            if ("CUSTOM".equalsIgnoreCase(action.getDetectLocationMode()) || "Custom Region".equalsIgnoreCase(action.getDetectLocationMode())) {
                modeDesc = "[Custom Region: " + action.getCustomRegionX() + ", " + action.getCustomRegionY() + "]";
            } else if ("FULL_SCREEN".equalsIgnoreCase(action.getDetectLocationMode()) || "Full Screen".equalsIgnoreCase(action.getDetectLocationMode())) {
                modeDesc = "[Full Screen]";
            }
            tvConditionSubtitle.setText(modeDesc);

            if (action.getImagePath() != null) {
                File imgFile = new File(action.getImagePath());
                if (imgFile.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                    if (bmp != null) {
                        ivConditionThumb.setImageBitmap(bmp);
                    }
                }
            }

            // عند الضغط على كرت الشرط يفتح نافذة Detect Location مباشرة
            card.findViewById(R.id.layoutNestedConditionCard).setOnClickListener(v -> showDetectLocationDialog(action));

            layoutActionListContainer.addView(card);
        }
    }

    private void showDetectLocationDialog(Action action) {
        targetEditingAction = action;
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_detect_location, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(layoutType);
        }

        RadioButton rbLocCaptured = dialogView.findViewById(R.id.rbLocCaptured);
        RadioButton rbLocCustom = dialogView.findViewById(R.id.rbLocCustom);
        RadioButton rbLocFullScreen = dialogView.findViewById(R.id.rbLocFullScreen);
        TextView tvCapturedCoordsDetails = dialogView.findViewById(R.id.tvCapturedCoordsDetails);
        tvDialogCustomRegionCoords = dialogView.findViewById(R.id.tvCustomRegionValues);
        ImageView btnPickCustomRegionFrame = dialogView.findViewById(R.id.btnPickCustomRegionFrame);
        Button btnLocCancel = dialogView.findViewById(R.id.btnLocCancel);
        Button btnLocSave = dialogView.findViewById(R.id.btnLocSave);

        tvCapturedCoordsDetails.setText("[" + action.getCropX() + ", " + action.getCropY() + ", " + action.getCropW() + ", " + action.getCropH() + "]");

        if (action.getCustomRegionW() > 0 && action.getCustomRegionH() > 0) {
            tvDialogCustomRegionCoords.setText(action.getCustomRegionX() + ", " + action.getCustomRegionY() + ", " + action.getCustomRegionW() + ", " + action.getCustomRegionH());
        }

        String mode = action.getDetectLocationMode();
        if (mode != null && (mode.equalsIgnoreCase("CUSTOM") || mode.equalsIgnoreCase("Custom Region"))) {
            rbLocCustom.setChecked(true);
            rbLocCaptured.setChecked(false);
            rbLocFullScreen.setChecked(false);
        } else if (mode != null && (mode.equalsIgnoreCase("FULL_SCREEN") || mode.equalsIgnoreCase("Full Screen"))) {
            rbLocFullScreen.setChecked(true);
            rbLocCaptured.setChecked(false);
            rbLocCustom.setChecked(false);
        } else {
            rbLocCaptured.setChecked(true);
            rbLocCustom.setChecked(false);
            rbLocFullScreen.setChecked(false);
        }

        rbLocCaptured.setOnClickListener(v -> { rbLocCustom.setChecked(false); rbLocFullScreen.setChecked(false); });
        rbLocCustom.setOnClickListener(v -> { rbLocCaptured.setChecked(false); rbLocFullScreen.setChecked(false); });
        rbLocFullScreen.setOnClickListener(v -> { rbLocCaptured.setChecked(false); rbLocCustom.setChecked(false); });

        // زر فتح إطار تحديد المنطقة المخصصة بصرياً
        btnPickCustomRegionFrame.setOnClickListener(v -> {
            rbLocCustom.setChecked(true);
            rbLocCaptured.setChecked(false);
            rbLocFullScreen.setChecked(false);
            Intent cropIntent = new Intent(FloatingWindowService.this, ImageCropPickerService.class);
            cropIntent.putExtra("is_custom_region", true);
            startService(cropIntent);
        });

        btnLocCancel.setOnClickListener(v -> dialog.dismiss());

        // حفظ الوضع مع المعالجة الصارمة المتوافقة مع VisionEngine
        btnLocSave.setOnClickListener(v -> {
            if (rbLocCaptured.isChecked()) {
                action.setDetectLocationMode("CAPTURED");
            } else if (rbLocCustom.isChecked()) {
                action.setDetectLocationMode("CUSTOM");
            } else if (rbLocFullScreen.isChecked()) {
                action.setDetectLocationMode("FULL_SCREEN");
            }
            dialog.dismiss();
            refreshHudActionCards();
            Toast.makeText(FloatingWindowService.this, "تم حفظ وضع الكشف: " + action.getDetectLocationMode(), Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void startAutomationExecution() {
        if (GlobalData.actionList == null || GlobalData.actionList.isEmpty()) {
            Toast.makeText(this, "لا توجد أكشنات لتشغيلها!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!AutoAccessibilityService.isRunning()) {
            Toast.makeText(this, "يرجى تفعيل خدمة إمكانية الوصول Auto أولاً!", Toast.LENGTH_LONG).show();
            return;
        }

        isRunningMacro = true;
        hudMenuView.setVisibility(View.GONE);
        floatingBadgeView.setVisibility(View.GONE);

        try {
            windowManager.addView(floatingStopView, stopParams);
        } catch (Exception ignored) {}

        macroThread = new Thread(() -> {
            while (isRunningMacro) {
                for (Action action : GlobalData.actionList) {
                    if (!isRunningMacro || action.isDisabled()) continue;

                    if (action.getDelayBeforeMs() > 0) {
                        try { Thread.sleep(action.getDelayBeforeMs()); } catch (InterruptedException e) { break; }
                    }

                    boolean shouldExecute = true;

                    // فحص شرط الصورة إن وجد
                    if (action.hasCondition() && "Image Appear".equals(action.getConditionType())) {
                        shouldExecute = false;
                        Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                        if (screen != null && action.getImagePath() != null) {
                            Bitmap template = BitmapFactory.decodeFile(action.getImagePath());
                            if (template != null) {
                                Point match = VisionEngine.findActionTarget(screen, template, action);
                                boolean found = (match != null);
                                shouldExecute = action.isNotAppear() ? !found : found;
                                template.recycle();
                            }
                            screen.recycle();
                        }
                    }

                    if (shouldExecute && AutoAccessibilityService.instance != null && isRunningMacro) {
                        String type = action.getType();
                        if ("Click (x, y)".equals(type)) {
                            AutoAccessibilityService.instance.click(action.getX() + action.getOffsetX(), action.getY() + action.getOffsetY());
                        } else if ("Click Image".equals(type) || action.getImagePath() != null) {
                            Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                            if (screen != null && action.getImagePath() != null) {
                                Bitmap template = BitmapFactory.decodeFile(action.getImagePath());
                                if (template != null) {
                                    Point match = VisionEngine.findActionTarget(screen, template, action);
                                    if (match != null) {
                                        // حساب المركز الدقيق للهدف المكتشف في Custom Region أو Full Screen لحظياً
                                        int clickX = match.x + (template.getWidth() / 2) + action.getOffsetX();
                                        int clickY = match.y + (template.getHeight() / 2) + action.getOffsetY();
                                        AutoAccessibilityService.instance.click(clickX, clickY);
                                    }
                                    template.recycle();
                                }
                                screen.recycle();
                            }
                        } else if ("Swipe".equals(type)) {
                            AutoAccessibilityService.instance.swipe(500, 1200, 500, 400, 400);
                        } else if ("Press Back".equals(type)) {
                            AutoAccessibilityService.instance.pressBack();
                        } else if ("Press Home".equals(type)) {
                            AutoAccessibilityService.instance.pressHome();
                        }
                    }

                    try {
                        Thread.sleep(action.getDelayAfterMs() > 0 ? action.getDelayAfterMs() : 500);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            new Handler(Looper.getMainLooper()).post(this::stopAutomationExecution);
        });

        macroThread.start();
    }

    private void stopAutomationExecution() {
        isRunningMacro = false;
        if (macroThread != null) {
            macroThread.interrupt();
            macroThread = null;
        }

        try {
            windowManager.removeView(floatingStopView);
        } catch (Exception ignored) {}

        hudMenuView.setVisibility(View.VISIBLE);
        floatingBadgeView.setVisibility(View.GONE);
        refreshHudActionCards();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunningMacro = false;
        if (customRegionReceiver != null) {
            unregisterReceiver(customRegionReceiver);
        }
        if (windowManager != null) {
            try { windowManager.removeView(floatingBadgeView); } catch (Exception ignored) {}
            try { windowManager.removeView(hudMenuView); } catch (Exception ignored) {}
            try { windowManager.removeView(floatingStopView); } catch (Exception ignored) {}
        }
    }
}
