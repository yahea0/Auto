package com.example.myautomationapp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.Button;
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
    private View floatingView;
    private View popupMenuView;
    private View hudBarView;
    
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams popupParams;
    private WindowManager.LayoutParams hudParams;
    
    private static final String CHANNEL_ID = "AutoServiceChannel";
    private boolean isPopupOpen = false;
    private boolean isHudOpen = false;
    private BroadcastReceiver coordReceiver;
    private BroadcastReceiver cropReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("resultCode")) {
            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra("data");
            if (resultCode == Activity.RESULT_OK && data != null) {
                ScreenCaptureManager.getInstance().init(this, resultCode, data);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, buildNotification(), 
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
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
                showConditionDialog(x, y);
            }
        };
        ContextCompat.registerReceiver(this, coordReceiver, new IntentFilter("COORDINATES_PICKED"), ContextCompat.RECEIVER_NOT_EXPORTED);

        cropReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int cropX = intent.getIntExtra("crop_x", 0);
                int cropY = intent.getIntExtra("crop_y", 0);
                int cropW = intent.getIntExtra("crop_w", 300);
                int cropH = intent.getIntExtra("crop_h", 150);
                int targetX = intent.getIntExtra("target_x", 500);
                int targetY = intent.getIntExtra("target_y", 1000);
                String imagePath = intent.getStringExtra("image_path");

                Action action = new Action("Click (x, y)", "Click [" + targetX + ", " + targetY + "] [C]", targetX, targetY);
                action.setHasCondition(true);
                action.setConditionType("Image Appear");
                action.setImageName("img_" + (GlobalData.actionList.size() + 1));
                action.setImagePath(imagePath);
                action.setSimilarity(70);
                action.setCropBounds(cropX, cropY, cropW, cropH);

                GlobalData.actionList.add(action);
                updateHudActionCards();
                openHudBar();
                Toast.makeText(FloatingWindowService.this, "تم حفظ الصورة واقترانها بالشرط!", Toast.LENGTH_SHORT).show();
            }
        };
        ContextCompat.registerReceiver(this, cropReceiver, new IntentFilter("IMAGE_TEMPLATE_CROPPED"), ContextCompat.RECEIVER_NOT_EXPORTED);
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
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
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
            closePopupMenu();
        } else {
            if (popupMenuView.getWindowToken() == null) windowManager.addView(popupMenuView, popupParams);
            isPopupOpen = true;
        }
    }

    private void closePopupMenu() {
        if (isPopupOpen && popupMenuView.getWindowToken() != null) {
            windowManager.removeView(popupMenuView);
            isPopupOpen = false;
        }
    }

    private void setupPopupMenu() {
        View btnManage = popupMenuView.findViewById(R.id.menuManageActions);
        if (btnManage != null) btnManage.setOnClickListener(v -> {
            closePopupMenu();
            openHudBar();
        });

        View btnRun = popupMenuView.findViewById(R.id.menuRunTest);
        if (btnRun != null) btnRun.setOnClickListener(v -> {
            closePopupMenu();
            runUnifiedMacro();
        });

        View btnSave = popupMenuView.findViewById(R.id.menuSave);
        if (btnSave != null) btnSave.setOnClickListener(v -> {
            closePopupMenu();
            Toast.makeText(this, "تم حفظ الماكرو بنجاح!", Toast.LENGTH_SHORT).show();
        });

        View btnMore = popupMenuView.findViewById(R.id.menuMore);
        if (btnMore != null) btnMore.setOnClickListener(v -> {
            closePopupMenu();
            showAiDiagnosticsDialog();
        });

        View btnExit = popupMenuView.findViewById(R.id.menuExit);
        if (btnExit != null) btnExit.setOnClickListener(v -> {
            closePopupMenu();
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

            if (action.isDisabled()) card.setAlpha(0.5f); else card.setAlpha(1.0f);

            TextView tvTitle = card.findViewById(R.id.tvActionTitle);
            TextView tvSubtitle = card.findViewById(R.id.tvActionSubtitle);
            TextView tvIndex = card.findViewById(R.id.tvActionIndex);
            View branchLayout = card.findViewById(R.id.layoutConditionBranch);

            tvTitle.setText(action.getDetail() != null ? action.getDetail() : action.getType());
            tvSubtitle.setText("[" + action.getClickStyle() + "] [Delay " + action.getDelayMs() + "ms]");
            tvIndex.setText(String.valueOf(index + 1));

            if (action.hasCondition()) {
                branchLayout.setVisibility(View.VISIBLE);
                TextView tvCondTitle = card.findViewById(R.id.tvConditionTitle);
                String state = action.isNotAppear() ? "[Not Appear]" : "[Appear]";
                tvCondTitle.setText("Image [" + action.getImageName() + "] " + state + " [" + action.getSimilarity() + "%]");

                ImageView ivThumb = card.findViewById(R.id.ivConditionThumb);
                if (ivThumb != null && action.getImagePath() != null) {
                    Bitmap thumbBmp = BitmapFactory.decodeFile(action.getImagePath());
                    if (thumbBmp != null) {
                        ivThumb.setImageBitmap(thumbBmp);
                        ivThumb.setPadding(0, 0, 0, 0);
                        ivThumb.setBackground(null);
                        ivThumb.setColorFilter(null);
                    }
                }

                View nestedCard = card.findViewById(R.id.layoutNestedConditionCard);
                nestedCard.setOnClickListener(v -> showConditionOptionsMenu(action));
            } else {
                branchLayout.setVisibility(View.GONE);
            }

            View mainClick = card.findViewById(R.id.layoutMainActionClick);
            mainClick.setOnClickListener(v -> showMacrorifyActionMenu(action, index));

            container.addView(card);
        }
    }

    private void showConditionDialog(int x, int y) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_condition_select, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        dialogView.findViewById(R.id.btnNoCondition).setOnClickListener(v -> {
            dialog.dismiss();
            Action clickAction = new Action("Click (x, y)", "Click [" + x + ", " + y + "] [C]", x, y);
            GlobalData.actionList.add(clickAction);
            updateHudActionCards();
            openHudBar();
            Toast.makeText(this, "تمت إضافة النقر: [No Condition]", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnImageAppear).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            Intent intent = new Intent(this, ImageCropPickerService.class);
            intent.putExtra("target_x", x);
            intent.putExtra("target_y", y);
            startService(intent);
        });

        dialog.show();
    }

    /**
     * فحص شرط الصورة الفائق وعرض نافذة الذكاء الاصطناعي مع سلسلة النسب المتاحة بنجاح
     */
    private void showConditionOptionsMenu(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_condition_options, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        TextView tvToggle = dialogView.findViewById(R.id.tvToggleAppearText);
        tvToggle.setText(action.isNotAppear() ? "Change to [Appear]" : "Change to [Not Appear]");
        dialogView.findViewById(R.id.optToggleAppear).setOnClickListener(v -> {
            dialog.dismiss();
            action.setNotAppear(!action.isNotAppear());
            updateHudActionCards();
            Toast.makeText(this, "تم تغيير حالة الشرط!", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.optEditSimilarity).setOnClickListener(v -> {
            dialog.dismiss();
            showEditSimilarityDialog(action);
        });

        // Test Condition مع النافذة الذكية الفخمة وقائمة النسب المتاحة
        dialogView.findViewById(R.id.optTestCondition).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();

            new Thread(() -> {
                try { Thread.sleep(120); } catch (Exception ignored) {}

                Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                new Handler(Looper.getMainLooper()).post(() -> {
                    openHudBar();

                    if (screen != null && action.getImagePath() != null) {
                        Bitmap template = BitmapFactory.decodeFile(action.getImagePath());
                        if (template != null) {
                            double exactSim = VisionEngine.compareSubRegionStrict(screen, template, action.getCropX(), action.getCropY());
                            int recommended = MacroAiInspector.getRecommendedSimilarity(exactSim);
                            String availableRates = MacroAiInspector.generateAvailablePassingPercentages(exactSim);

                            showAiResultProDialog(action, exactSim, recommended, availableRates);
                        }
                    } else {
                        Toast.makeText(FloatingWindowService.this, "تعذر التقاط الشاشة!", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        dialogView.findViewById(R.id.optDeleteCondition).setOnClickListener(v -> {
            dialog.dismiss();
            action.setHasCondition(false);
            updateHudActionCards();
            Toast.makeText(this, "تم حذف الشرط!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    /**
     * نافذة فحص الذكاء الاصطناعي الفخمة مع بطاقات التحليل وسلسلة النسب المتاحة
     */
    private void showAiResultProDialog(Action action, double exactSim, int recommendedSim, String availableRates) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_ai_test_result, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        TextView tvExact = dialogView.findViewById(R.id.tvAiExactSim);
        TextView tvStatus = dialogView.findViewById(R.id.tvAiCurrentStatusBadge);
        TextView tvRates = dialogView.findViewById(R.id.tvAiAvailableRates);
        TextView tvSetting = dialogView.findViewById(R.id.tvAiTargetSetting);
        Button btnApply = dialogView.findViewById(R.id.btnApplyRecommendedSim);
        Button btnClose = dialogView.findViewById(R.id.btnCloseAiDialog);

        tvExact.setText(String.format("%.1f", exactSim) + "%");
        tvRates.setText(availableRates);
        tvSetting.setText("النسبة المضبوطة حالياً بالأكشن: " + action.getSimilarity() + "%");

        boolean willPass = (exactSim >= action.getSimilarity());
        if (willPass) {
            tvStatus.setText("✅ شرط مكتمل! الماكرو سينقر بنجاح الآن");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            tvExact.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        } else {
            tvStatus.setText("⚠️ لن ينقر! النسبة الحالية أقل من المطلوب");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            tvExact.setTextColor(android.graphics.Color.parseColor("#FF5252"));
        }

        btnApply.setText("⚡ تطبيق النسبة الذكية (" + recommendedSim + "%)");
        btnApply.setOnClickListener(v -> {
            dialog.dismiss();
            action.setSimilarity(recommendedSim);
            updateHudActionCards();
            Toast.makeText(this, "تم تحديث النسبة بنجاح إلى: " + recommendedSim + "%", Toast.LENGTH_SHORT).show();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showAiDiagnosticsDialog() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("🤖 تقرير الذكاء الاصطناعي للمشروع");
        builder.setMessage(MacroAiInspector.getSystemDiagnosticReport(this));
        builder.setPositiveButton("حسناً", null);

        AlertDialog d = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            d.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        d.show();
    }

    private void showEditSimilarityDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Edit Similarity % (1 - 100)");

        final EditText input = new EditText(themedContext);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(action.getSimilarity()));
        builder.setView(input);

        builder.setPositiveButton("حفظ", (dialog, which) -> {
            String val = input.getText().toString();
            int sim = val.isEmpty() ? 70 : Integer.parseInt(val);
            if (sim > 100) sim = 100;
            if (sim < 1) sim = 1;
            action.setSimilarity(sim);
            updateHudActionCards();
        });
        builder.setNegativeButton("إلغاء", null);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.show();
    }

    private void showMacrorifyActionMenu(Action action, int index) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_action_options, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        dialogView.findViewById(R.id.optEditCoordinates).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            Intent intent = new Intent(this, CoordinatePickerService.class);
            startService(intent);
        });

        dialogView.findViewById(R.id.optEditClickStyle).setOnClickListener(v -> {
            dialog.dismiss();
            showClickStyleDialog(action);
        });

        dialogView.findViewById(R.id.optEditDelay).setOnClickListener(v -> {
            dialog.dismiss();
            showEditDelayDialog(action);
        });

        dialogView.findViewById(R.id.optTestAction).setOnClickListener(v -> {
            dialog.dismiss();
            executeSingleActionNow(action);
            Toast.makeText(this, "تم تنفيذ النقر فوراً!", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.optAddCondition).setOnClickListener(v -> {
            dialog.dismiss();
            showConditionDialog(action.getX(), action.getY());
        });

        dialogView.findViewById(R.id.optCopy).setOnClickListener(v -> {
            dialog.dismiss();
            Action copy = new Action(action.getType(), action.getDetail(), action.getX(), action.getY());
            copy.setDelayMs(action.getDelayMs());
            copy.setClickStyle(action.getClickStyle());
            copy.setHasCondition(action.hasCondition());
            copy.setConditionType(action.getConditionType());
            copy.setImagePath(action.getImagePath());
            copy.setSimilarity(action.getSimilarity());
            GlobalData.actionList.add(index + 1, copy);
            updateHudActionCards();
            Toast.makeText(this, "تم نسخ الأكشن!", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.optDelete).setOnClickListener(v -> {
            dialog.dismiss();
            GlobalData.actionList.remove(index);
            updateHudActionCards();
            Toast.makeText(this, "تم حذف الأكشن!", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.optDisable).setOnClickListener(v -> {
            dialog.dismiss();
            action.setDisabled(!action.isDisabled());
            updateHudActionCards();
            Toast.makeText(this, action.isDisabled() ? "تم تعطيل الأكشن" : "تم تفعيل الأكشن", Toast.LENGTH_SHORT).show();
        });

        View.OnClickListener simpleDismiss = v -> {
            dialog.dismiss();
            Toast.makeText(this, "قريباً", Toast.LENGTH_SHORT).show();
        };
        dialogView.findViewById(R.id.optEditScaling).setOnClickListener(simpleDismiss);
        dialogView.findViewById(R.id.optAddAbove).setOnClickListener(simpleDismiss);
        dialogView.findViewById(R.id.optReplace).setOnClickListener(simpleDismiss);
        dialogView.findViewById(R.id.optConvertCustom).setOnClickListener(simpleDismiss);
        dialogView.findViewById(R.id.optCut).setOnClickListener(simpleDismiss);

        dialog.show();
    }

    private void showClickStyleDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Edit Click Style");
        String[] styles = {"Single Click", "Double Click", "Long Press"};

        builder.setItems(styles, (d, which) -> {
            action.setClickStyle(styles[which]);
            updateHudActionCards();
            Toast.makeText(this, "النمط: " + styles[which], Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
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
            runUnifiedMacro();
        });

        View btnAdd = hudBarView.findViewById(R.id.btnAddActionHud);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> showFullActionDialog());

        View btnVar = hudBarView.findViewById(R.id.btnVariables);
        if (btnVar != null) btnVar.setOnClickListener(v -> showAiDiagnosticsDialog());
    }

    /**
     * تشغيل الماكرو مع خوارزمية الرؤية الفائقة
     */
    private void runUnifiedMacro() {
        if (GlobalData.actionList.isEmpty()) {
            Toast.makeText(this, "لا يوجد أكشنات للتشغيل!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AutoAccessibilityService.isRunning()) {
            Toast.makeText(this, "يرجى تفعيل خدمة إمكانية الوصول أولاً!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }

        closeHudBar();
        closePopupMenu();

        Toast.makeText(this, "تشغيل...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try { Thread.sleep(120); } catch (InterruptedException ignored) {}

            for (int i = 0; i < GlobalData.actionList.size(); i++) {
                Action action = GlobalData.actionList.get(i);
                if (action.isDisabled()) continue;

                if (AutoAccessibilityService.instance != null) {
                    String type = action.getType();
                    if ("Click (x, y)".equals(type)) {

                        if (action.hasCondition() && "Image Appear".equals(action.getConditionType())) {
                            boolean conditionMet = false;
                            if (action.getImagePath() != null) {
                                Bitmap template = BitmapFactory.decodeFile(action.getImagePath());
                                Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                                if (template != null && screen != null) {
                                    double currentSim = VisionEngine.compareSubRegionStrict(screen, template, action.getCropX(), action.getCropY());
                                    boolean detected = (currentSim >= action.getSimilarity());
                                    conditionMet = action.isNotAppear() ? !detected : detected;
                                }
                            }
                            if (!conditionMet) continue;
                        }

                        executeSingleActionNow(action);

                    } else if ("Press Back".equals(type)) {
                        AutoAccessibilityService.instance.pressBack();
                    }
                }

                if (i < GlobalData.actionList.size() - 1 && action.getDelayMs() > 0) {
                    try { Thread.sleep(action.getDelayMs()); } catch (InterruptedException ignored) {}
                }
            }
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(FloatingWindowService.this, "اكتمل التشغيل!", Toast.LENGTH_SHORT).show()
            );
        }).start();
    }

    private void executeSingleActionNow(Action action) {
        if (AutoAccessibilityService.instance == null) return;
        if ("Double Click".equals(action.getClickStyle())) {
            AutoAccessibilityService.instance.doubleClick(action.getX(), action.getY());
        } else if ("Long Press".equals(action.getClickStyle())) {
            AutoAccessibilityService.instance.longClick(action.getX(), action.getY());
        } else {
            AutoAccessibilityService.instance.click(action.getX(), action.getY());
        }
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

        dialogView.findViewById(R.id.btnClickXY).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            Intent intent = new Intent(this, CoordinatePickerService.class);
            startService(intent);
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
                .setContentTitle("Macrorify AI Engine")
                .setContentText("الخدمة ونظام الرؤية الفائقة نشطان")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (coordReceiver != null) try { unregisterReceiver(coordReceiver); } catch (Exception ignored) {}
        if (cropReceiver != null) try { unregisterReceiver(cropReceiver); } catch (Exception ignored) {}
        if (floatingView != null && windowManager != null) try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
        if (popupMenuView != null && windowManager != null) try { windowManager.removeView(popupMenuView); } catch (Exception ignored) {}
        if (hudBarView != null && windowManager != null) try { windowManager.removeView(hudBarView); } catch (Exception ignored) {}
    }
}
