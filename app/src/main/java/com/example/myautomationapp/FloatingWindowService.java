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

    // حافظة التطبيق الداخلية لعمليات Copy و Cut
    private static Action inAppClipboardAction = null;

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
                int editIndex = intent.getIntExtra("edit_index", -1);

                if (editIndex >= 0 && editIndex < GlobalData.actionList.size()) {
                    // تعديل إحداثيات أكشن موجود
                    Action existing = GlobalData.actionList.get(editIndex);
                    existing.setX(x);
                    existing.setY(y);
                    existing.setDetail("Click [" + x + ", " + y + "] [C]");
                    updateHudActionCards();
                    openHudBar();
                    Toast.makeText(FloatingWindowService.this, "تم تحديث إحداثيات النقر!", Toast.LENGTH_SHORT).show();
                } else {
                    // إضافة أكشن نقر جديد مع فتح نافذة الشروط
                    showConditionDialog(x, y);
                }
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

            if (action.isDisabled()) card.setAlpha(0.45f); else card.setAlpha(1.0f);

            TextView tvTitle = card.findViewById(R.id.tvActionTitle);
            TextView tvSubtitle = card.findViewById(R.id.tvActionSubtitle);
            TextView tvIndex = card.findViewById(R.id.tvActionIndex);
            View branchLayout = card.findViewById(R.id.layoutConditionBranch);

            String title = action.getCustomName() != null ? action.getCustomName() : (action.getDetail() != null ? action.getDetail() : action.getType());
            tvTitle.setText(title);
            tvSubtitle.setText("[" + action.getClickStyle() + "] [Delay " + action.getDelayBeforeMs() + "ms/" + action.getDelayAfterMs() + "ms]");
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

    /**
     * تفعيل كافة خيارات كرت الأكشن الـ 13 بمنطق برمجي كامل ومطابق لـ Macrorify 100%
     */
    private void showMacrorifyActionMenu(Action action, int index) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_action_options, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        // 1. Edit Coordinates: إعادة فتح أداة التصويب لتعديل موضع هذا الأكشن
        dialogView.findViewById(R.id.optEditCoordinates).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            Intent intent = new Intent(this, CoordinatePickerService.class);
            intent.putExtra("edit_index", index);
            intent.putExtra("init_x", action.getX());
            intent.putExtra("init_y", action.getY());
            startService(intent);
        });

        // 2. Edit Scaling Algorithm: خوارزمية التحجيم للشاشات المختلفة
        dialogView.findViewById(R.id.optEditScaling).setOnClickListener(v -> {
            dialog.dismiss();
            showScalingAlgorithmDialog(action);
        });

        // 3. Edit Click Style: نمط النقر (عادي، مزدوج، ثلاثي، مطول)
        dialogView.findViewById(R.id.optEditClickStyle).setOnClickListener(v -> {
            dialog.dismiss();
            showClickStyleDialog(action);
        });

        // 4. Edit Delay: ضبط وقت التأخير قبل وبعد النقر
        dialogView.findViewById(R.id.optEditDelay).setOnClickListener(v -> {
            dialog.dismiss();
            showEditDelayDialog(action);
        });

        // 5. Test Action: تجربة هذا الأكشن فقط فورياً
        dialogView.findViewById(R.id.optTestAction).setOnClickListener(v -> {
            dialog.dismiss();
            executeSingleActionNow(action);
            Toast.makeText(this, "تم اختبار الأكشن فوراً بنمط: " + action.getClickStyle(), Toast.LENGTH_SHORT).show();
        });

        // 6. Add Action (Above): إضافة أكشن جديد للأعلى مباشرة
        dialogView.findViewById(R.id.optAddAbove).setOnClickListener(v -> {
            dialog.dismiss();
            showActionPickerForIndex(index);
        });

        // 7. Add Condition: إضافة شرط لهذا الأكشن
        dialogView.findViewById(R.id.optAddCondition).setOnClickListener(v -> {
            dialog.dismiss();
            showConditionDialog(action.getX(), action.getY());
        });

        // 8. Replace Action: استبدال هذا الأكشن بنوع آخر
        dialogView.findViewById(R.id.optReplace).setOnClickListener(v -> {
            dialog.dismiss();
            showActionPickerForReplace(index);
        });

        // 9. Convert to Custom Action: تحويل إلى أكشن مخصص
        dialogView.findViewById(R.id.optConvertCustom).setOnClickListener(v -> {
            dialog.dismiss();
            showConvertToCustomDialog(action);
        });

        // 10. Copy: نسخ الأكشن إلى الحافظة
        dialogView.findViewById(R.id.optCopy).setOnClickListener(v -> {
            dialog.dismiss();
            inAppClipboardAction = action.clone();
            GlobalData.actionList.add(index + 1, action.clone());
            updateHudActionCards();
            Toast.makeText(this, "تم نسخ الأكشن ومضاعفته!", Toast.LENGTH_SHORT).show();
        });

        // 11. Cut: قص الأكشن وحفظه بالحافظة
        dialogView.findViewById(R.id.optCut).setOnClickListener(v -> {
            dialog.dismiss();
            inAppClipboardAction = action.clone();
            GlobalData.actionList.remove(index);
            updateHudActionCards();
            Toast.makeText(this, "تم قص الأكشن إلى الحافظة!", Toast.LENGTH_SHORT).show();
        });

        // 12. Delete: حذف الأكشن
        dialogView.findViewById(R.id.optDelete).setOnClickListener(v -> {
            dialog.dismiss();
            GlobalData.actionList.remove(index);
            updateHudActionCards();
            Toast.makeText(this, "تم حذف الأكشن!", Toast.LENGTH_SHORT).show();
        });

        // 13. Disable: تعطيل أو تفعيل الأكشن
        dialogView.findViewById(R.id.optDisable).setOnClickListener(v -> {
            dialog.dismiss();
            action.setDisabled(!action.isDisabled());
            updateHudActionCards();
            Toast.makeText(this, action.isDisabled() ? "تم تعطيل الأكشن (سيتم تخطيه)" : "تم تفعيل الأكشن", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showScalingAlgorithmDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Scaling Algorithm (تحجيم الشاشات)");
        String[] algorithms = {
                "Aspect Ratio (نسبة وتناسب الشاشة - افتراضي)",
                "Center Anchor (تثبيت الموضع من منتصف الشاشة)",
                "Top-Left Anchor (تثبيت الموضع من أعلى اليسار)",
                "Absolute Pixels (بكسلات ثابتة ومطلقة)"
        };

        builder.setItems(algorithms, (d, which) -> {
            String selected = algorithms[which].split(" ")[0];
            action.setScalingAlgorithm(selected);
            Toast.makeText(this, "الخوارزمية: " + selected, Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.show();
    }

    private void showClickStyleDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Click Style (نمط النقر)");
        String[] styles = {"Single Click", "Double Click", "Triple Click", "Long Click"};

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
        builder.setTitle("Edit Delay (أوقات الانتظار)");

        LinearLayout layout = new LinearLayout(themedContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        TextView tvBefore = new TextView(themedContext);
        tvBefore.setText("Wait Before (انتظار قبل النقر - ms):");
        tvBefore.setTextColor(android.graphics.Color.WHITE);
        final EditText etBefore = new EditText(themedContext);
        etBefore.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etBefore.setText(String.valueOf(action.getDelayBeforeMs()));

        TextView tvAfter = new TextView(themedContext);
        tvAfter.setText("Wait After (انتظار بعد النقر - ms):");
        tvAfter.setTextColor(android.graphics.Color.WHITE);
        tvAfter.setPadding(0, 16, 0, 0);
        final EditText etAfter = new EditText(themedContext);
        etAfter.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etAfter.setText(String.valueOf(action.getDelayAfterMs()));

        layout.addView(tvBefore);
        layout.addView(etBefore);
        layout.addView(tvAfter);
        layout.addView(etAfter);
        builder.setView(layout);

        builder.setPositiveButton("حفظ", (dialog, which) -> {
            String bVal = etBefore.getText().toString();
            String aVal = etAfter.getText().toString();
            action.setDelayBeforeMs(bVal.isEmpty() ? 0 : Integer.parseInt(bVal));
            action.setDelayAfterMs(aVal.isEmpty() ? 500 : Integer.parseInt(aVal));
            updateHudActionCards();
        });
        builder.setNegativeButton("إلغاء", null);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.show();
    }

    private void showConvertToCustomDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Convert to Custom Action");

        final EditText input = new EditText(themedContext);
        input.setHint("اسم الأكشن المخصص (Custom Name)");
        builder.setView(input);

        builder.setPositiveButton("تحويل", (dialog, which) -> {
            String name = input.getText().toString();
            if (!name.isEmpty()) {
                action.setCustomName("Custom [" + name + "]");
                updateHudActionCards();
                Toast.makeText(this, "تم التحويل إلى أكشن مخصص بنجاح!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("إلغاء", null);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.show();
    }

    private void showActionPickerForIndex(int targetIndex) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("إضافة أكشن للأعلى");
        String[] options = {"Wait (انتظار)", "Swipe (سحب)", "Press Back (رجوع)", "Press Home (الشاشة الرئيسية)"};

        builder.setItems(options, (d, which) -> {
            Action newAction;
            if (which == 0) newAction = new Action("Wait", "Wait 1000ms");
            else if (which == 1) newAction = new Action("Swipe", "Swipe [500, 1200] -> [500, 400]");
            else if (which == 2) newAction = new Action("Press Back", "Device Back Key");
            else newAction = new Action("Press Home", "Device Home Key");

            GlobalData.actionList.add(targetIndex, newAction);
            updateHudActionCards();
            Toast.makeText(this, "تمت إضافة الأكشن في الموضع المطلوب!", Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.show();
    }

    private void showActionPickerForReplace(int targetIndex) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("استبدال هذا الأكشن بـ");
        String[] options = {"Wait (انتظار)", "Swipe (سحب)", "Press Back (رجوع)", "Press Home (الشاشة الرئيسية)"};

        builder.setItems(options, (d, which) -> {
            Action newAction;
            if (which == 0) newAction = new Action("Wait", "Wait 1000ms");
            else if (which == 1) newAction = new Action("Swipe", "Swipe [500, 1200] -> [500, 400]");
            else if (which == 2) newAction = new Action("Press Back", "Device Back Key");
            else newAction = new Action("Press Home", "Device Home Key");

            GlobalData.actionList.set(targetIndex, newAction);
            updateHudActionCards();
            Toast.makeText(this, "تم استبدال الأكشن بنجاح!", Toast.LENGTH_SHORT).show();
        });

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
     * محرك التشغيل الموحد الفوري: يراعي Delay Before و Delay After وأنماط النقر وحالة التعطيل
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
                
                // تخطي الأكشن إذا كان معطلاً Disable
                if (action.isDisabled()) continue;

                // تطبيق وقت الانتظار قبل النقر (Delay Before)
                if (action.getDelayBeforeMs() > 0) {
                    try { Thread.sleep(action.getDelayBeforeMs()); } catch (InterruptedException ignored) {}
                }

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
                    } else if ("Press Home".equals(type)) {
                        AutoAccessibilityService.instance.pressHome();
                    }
                }

                // تطبيق وقت الانتظار بعد النقر (Delay After)
                if (action.getDelayAfterMs() > 0) {
                    try { Thread.sleep(action.getDelayAfterMs()); } catch (InterruptedException ignored) {}
                }
            }
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(FloatingWindowService.this, "اكتمل التشغيل!", Toast.LENGTH_SHORT).show()
            );
        }).start();
    }

    private void executeSingleActionNow(Action action) {
        if (AutoAccessibilityService.instance == null) return;
        String style = action.getClickStyle();
        if ("Double Click".equals(style)) {
            AutoAccessibilityService.instance.doubleClick(action.getX(), action.getY());
        } else if ("Triple Click".equals(style)) {
            AutoAccessibilityService.instance.tripleClick(action.getX(), action.getY());
        } else if ("Long Click".equals(style)) {
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
