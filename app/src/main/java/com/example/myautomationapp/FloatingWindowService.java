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
import android.graphics.Point;
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
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private View popupMenuView;
    private View hudBarView;
    private View stopButtonView;
    
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams popupParams;
    private WindowManager.LayoutParams hudParams;
    private WindowManager.LayoutParams stopParams;
    
    private static final String CHANNEL_ID = "AutoServiceChannel";
    private boolean isPopupOpen = false;
    private boolean isHudOpen = false;
    private boolean isStopButtonVisible = false;
    private volatile boolean isMacroRunning = false;
    
    private BroadcastReceiver coordReceiver;
    private BroadcastReceiver cropReceiver;
    private BroadcastReceiver clickImageReceiver;
    private BroadcastReceiver customRegionReceiver;
    private BroadcastReceiver alternateTemplateReceiver;
    private BroadcastReceiver anchorReceiver;

    private static Action inAppClipboardAction = null;
    private Action currentEditingRegionAction = null;
    private Action currentTargetForAlternateTemplate = null;
    private TemplateItem currentEditingAnchorItem = null;

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
                    Action existing = GlobalData.actionList.get(editIndex);
                    existing.setX(x); existing.setY(y);
                    existing.setDetail("Click [" + x + ", " + y + "] [C]");
                    updateHudActionCards();
                    openHudBar();
                    Toast.makeText(FloatingWindowService.this, "تم تحديث إحداثيات النقر!", Toast.LENGTH_SHORT).show();
                } else {
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
                String name = new File(imagePath).getName().replace(".png", "");
                action.setImageName(name);
                action.setImagePath(imagePath);
                action.setSimilarity(70);
                action.setCropBounds(cropX, cropY, cropW, cropH);

                GlobalData.actionList.add(action);
                updateHudActionCards();
                openHudBar();
                Toast.makeText(FloatingWindowService.this, "تم حفظ الصورة بنجاح!", Toast.LENGTH_SHORT).show();
            }
        };
        ContextCompat.registerReceiver(this, cropReceiver, new IntentFilter("IMAGE_TEMPLATE_CROPPED"), ContextCompat.RECEIVER_NOT_EXPORTED);

        clickImageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int cropX = intent.getIntExtra("crop_x", 0);
                int cropY = intent.getIntExtra("crop_y", 0);
                int cropW = intent.getIntExtra("crop_w", 250);
                int cropH = intent.getIntExtra("crop_h", 150);
                String imagePath = intent.getStringExtra("image_path");

                String name = new File(imagePath).getName().replace(".png", "");
                Action action = new Action("Click Image", "Click [" + name + "] [0+/0+]");
                action.setImageName(name);
                action.setImagePath(imagePath);
                action.setSimilarity(70);
                action.setHasCondition(true);
                action.setConditionType("Image Appear");
                action.setCropBounds(cropX, cropY, cropW, cropH);
                action.setX(cropX + (cropW / 2));
                action.setY(cropY + (cropH / 2));

                GlobalData.actionList.add(action);
                updateHudActionCards();
                openHudBar();
                Toast.makeText(FloatingWindowService.this, "تمت إضافة Click Image!", Toast.LENGTH_SHORT).show();
            }
        };
        ContextCompat.registerReceiver(this, clickImageReceiver, new IntentFilter("CLICK_IMAGE_ACTION_CROPPED"), ContextCompat.RECEIVER_NOT_EXPORTED);

        customRegionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (currentEditingRegionAction != null) {
                    int rx = intent.getIntExtra("region_x", 0);
                    int ry = intent.getIntExtra("region_y", 0);
                    int rw = intent.getIntExtra("region_w", 300);
                    int rh = intent.getIntExtra("region_h", 300);
                    currentEditingRegionAction.setCustomRegion(rx, ry, rw, rh);
                    currentEditingRegionAction.setDetectLocationMode("CUSTOM");
                    updateHudActionCards();
                    openHudBar();
                    Toast.makeText(FloatingWindowService.this, "تم حفظ منطقة البحث المخصصة!", Toast.LENGTH_SHORT).show();
                }
            }
        };
        ContextCompat.registerReceiver(this, customRegionReceiver, new IntentFilter("CUSTOM_REGION_SELECTED"), ContextCompat.RECEIVER_NOT_EXPORTED);

        alternateTemplateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (currentTargetForAlternateTemplate != null) {
                    String altPath = intent.getStringExtra("image_path");
                    if (altPath != null) {
                        String name = new File(altPath).getName().replace(".png", "");
                        TemplateItem newItem = new TemplateItem("alt_" + System.currentTimeMillis(), altPath, name);
                        newItem.setSimilarity(currentTargetForAlternateTemplate.getSimilarity());
                        currentTargetForAlternateTemplate.addTemplateItem(newItem);
                        updateHudActionCards();
                        openHudBar();
                        Toast.makeText(FloatingWindowService.this, "تم تدريب نموذج جديد بنجاح!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        ContextCompat.registerReceiver(this, alternateTemplateReceiver, new IntentFilter("ALTERNATE_TEMPLATE_CROPPED"), ContextCompat.RECEIVER_NOT_EXPORTED);

        anchorReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (currentEditingAnchorItem != null) {
                    String anchorPath = intent.getStringExtra("anchor_path");
                    int relX = intent.getIntExtra("rel_x", 0);
                    int relY = intent.getIntExtra("rel_y", 0);
                    if (anchorPath != null) {
                        currentEditingAnchorItem.setHasAnchor(true);
                        currentEditingAnchorItem.setAnchorImagePath(anchorPath);
                        currentEditingAnchorItem.setAnchorRelativeX(relX);
                        currentEditingAnchorItem.setAnchorRelativeY(relY);
                        updateHudActionCards();
                        openHudBar();
                        Toast.makeText(FloatingWindowService.this, "تم حفظ العلامة المرجعية بنجاح!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        ContextCompat.registerReceiver(this, anchorReceiver, new IntentFilter("ANCHOR_TEMPLATE_CROPPED"), ContextCompat.RECEIVER_NOT_EXPORTED);
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

        stopButtonView = LayoutInflater.from(themedContext).inflate(R.layout.floating_stop_button, null);
        stopParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        stopParams.gravity = Gravity.BOTTOM | Gravity.START;
        stopParams.x = 40;
        stopParams.y = 100;

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            stopSelf();
            return;
        }

        setupFloatingDrag();
        setupStopButton();
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

    private void setupStopButton() {
        stopButtonView.setOnClickListener(v -> stopMacroExecution());
    }

    private void showStopButtonOverlay() {
        if (!isStopButtonVisible && stopButtonView.getWindowToken() == null) {
            windowManager.addView(stopButtonView, stopParams);
            isStopButtonVisible = true;
        }
    }

    private void hideStopButtonOverlay() {
        if (isStopButtonVisible && stopButtonView.getWindowToken() != null) {
            windowManager.removeView(stopButtonView);
            isStopButtonVisible = false;
        }
    }

    private void stopMacroExecution() {
        isMacroRunning = false;
        hideStopButtonOverlay();
        if (floatingView != null) floatingView.setVisibility(View.VISIBLE);
        Toast.makeText(this, "تم إيقاف الماكرو!", Toast.LENGTH_SHORT).show();
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
            startMacroLoopExecution();
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
            ImageView ivIcon = card.findViewById(R.id.ivActionIcon);
            View branchLayout = card.findViewById(R.id.layoutConditionBranch);

            int poolSize = Math.max(1, action.getTemplatePool().size());

            if ("Click Image".equals(action.getType())) {
                ivIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                String offsetTag = "[" + (action.getOffsetX() >= 0 ? action.getOffsetX() + "+" : action.getOffsetX()) + "/" + (action.getOffsetY() >= 0 ? action.getOffsetY() + "+" : action.getOffsetY()) + "]";
                tvTitle.setText("Click [" + action.getImageName() + "] " + offsetTag);
                tvSubtitle.setText("[X1] [Delay " + action.getDelayBeforeMs() + "ms/" + action.getDelayAfterMs() + "ms]");

                branchLayout.setVisibility(View.VISIBLE);
                TextView tvCondTitle = card.findViewById(R.id.tvConditionTitle);
                tvCondTitle.setText("Image [" + action.getImageName() + "] [x" + poolSize + "] [" + action.getSimilarity() + "%]");

                TextView tvCondSub = card.findViewById(R.id.tvConditionSubtitle);
                tvCondSub.setText(getDisplayLocationLabel(action));

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
                nestedCard.setOnClickListener(v -> showImageConditionSubMenu(action));

                View mainClick = card.findViewById(R.id.layoutMainActionClick);
                mainClick.setOnClickListener(v -> showClickImageActionMenu(action, index));

            } else {
                ivIcon.setImageResource(android.R.drawable.ic_menu_compass);
                String title = action.getCustomName() != null ? action.getCustomName() : (action.getDetail() != null ? action.getDetail() : action.getType());
                tvTitle.setText(title);
                tvSubtitle.setText("[" + action.getClickStyle() + "] [Delay " + action.getDelayBeforeMs() + "ms/" + action.getDelayAfterMs() + "ms]");

                if (action.hasCondition()) {
                    branchLayout.setVisibility(View.VISIBLE);
                    TextView tvCondTitle = card.findViewById(R.id.tvConditionTitle);
                    String state = action.isNotAppear() ? "[Not Appear]" : "[Appear]";
                    tvCondTitle.setText("Image [" + action.getImageName() + "] [x" + poolSize + "] " + state + " [" + action.getSimilarity() + "%]");

                    TextView tvCondSub = card.findViewById(R.id.tvConditionSubtitle);
                    tvCondSub.setText(getDisplayLocationLabel(action));

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
                    nestedCard.setOnClickListener(v -> showImageConditionSubMenu(action));
                } else {
                    branchLayout.setVisibility(View.GONE);
                }

                View mainClick = card.findViewById(R.id.layoutMainActionClick);
                mainClick.setOnClickListener(v -> showMacrorifyActionMenu(action, index));
            }

            tvIndex.setText(String.valueOf(index + 1));
            container.addView(card);
        }
    }

    private String getDisplayLocationLabel(Action action) {
        String mode = action.getDetectLocationMode();
        if (mode == null) return "[Captured Location]";
        String m = mode.toUpperCase();
        if (m.contains("CUSTOM")) {
            return "[Custom Region]";
        } else if (m.contains("FULL")) {
            return "[Full Screen]";
        }
        return "[Captured Location]";
    }

    private void showDetectLocationDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_detect_location, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        RadioButton rbCaptured = dialogView.findViewById(R.id.rbLocCaptured);
        RadioButton rbCustom = dialogView.findViewById(R.id.rbLocCustom);
        RadioButton rbFullScreen = dialogView.findViewById(R.id.rbLocFullScreen);
        TextView tvCapturedDetails = dialogView.findViewById(R.id.tvCapturedCoordsDetails);
        TextView tvCustomDetails = dialogView.findViewById(R.id.tvCustomRegionValues);
        ImageView btnFrame = dialogView.findViewById(R.id.btnPickCustomRegionFrame);

        String currentMode = action.getDetectLocationMode();
        setDetectLocationRadio(rbCaptured, rbCustom, rbFullScreen, currentMode);

        tvCapturedDetails.setText("[" + action.getCropX() + ", " + action.getCropY() + ", " + action.getCropW() + ", " + action.getCropH() + "]");
        if (action.getCustomRegionW() > 0) {
            tvCustomDetails.setText(action.getCustomRegionX() + ", " + action.getCustomRegionY() + ", " + action.getCustomRegionW() + ", " + action.getCustomRegionH());
        }

        View.OnClickListener selectCaptured = v -> setDetectLocationRadio(rbCaptured, rbCustom, rbFullScreen, "CAPTURED");
        dialogView.findViewById(R.id.rowLocCaptured).setOnClickListener(selectCaptured);
        rbCaptured.setOnClickListener(selectCaptured);

        View.OnClickListener selectCustom = v -> setDetectLocationRadio(rbCaptured, rbCustom, rbFullScreen, "CUSTOM");
        dialogView.findViewById(R.id.rowLocCustom).setOnClickListener(selectCustom);
        rbCustom.setOnClickListener(selectCustom);

        View.OnClickListener selectFullScreen = v -> setDetectLocationRadio(rbCaptured, rbCustom, rbFullScreen, "FULL_SCREEN");
        dialogView.findViewById(R.id.rowLocFullScreen).setOnClickListener(selectFullScreen);
        rbFullScreen.setOnClickListener(selectFullScreen);

        btnFrame.setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            currentEditingRegionAction = action;
            Intent intent = new Intent(this, ImageCropPickerService.class);
            intent.putExtra("is_custom_region", true);
            startService(intent);
        });

        dialogView.findViewById(R.id.btnLocSave).setOnClickListener(v -> {
            if (rbCaptured.isChecked()) {
                action.setDetectLocationMode("CAPTURED");
            } else if (rbCustom.isChecked()) {
                action.setDetectLocationMode("CUSTOM");
            } else if (rbFullScreen.isChecked()) {
                action.setDetectLocationMode("FULL_SCREEN");
            }
            updateHudActionCards();
            dialog.dismiss();
            Toast.makeText(this, "تم حفظ نمط منطقة الفحص: " + getDisplayLocationLabel(action), Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnLocCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setDetectLocationRadio(RadioButton rbCap, RadioButton rbCust, RadioButton rbFull, String mode) {
        String m = (mode == null) ? "CAPTURED" : mode.toUpperCase();
        rbCap.setChecked(m.contains("CAPTUR"));
        rbCust.setChecked(m.contains("CUSTOM"));
        rbFull.setChecked(m.contains("FULL"));
    }

    private void showClickImageActionMenu(Action action, int index) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_click_image_options, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        dialogView.findViewById(R.id.optImgChangeImage).setOnClickListener(v -> {
            dialog.dismiss();
            showImageSourcePicker(action, true);
        });

        dialogView.findViewById(R.id.optImgEditOffset).setOnClickListener(v -> {
            dialog.dismiss();
            showEditOffsetDialog(action);
        });

        dialogView.findViewById(R.id.optImgEditClickStyle).setOnClickListener(v -> {
            dialog.dismiss();
            showClickStyleDialog(action);
        });

        dialogView.findViewById(R.id.optImgEditDelay).setOnClickListener(v -> {
            dialog.dismiss();
            showEditDelayDialog(action);
        });

        dialogView.findViewById(R.id.optImgEditImage).setOnClickListener(v -> {
            dialog.dismiss();
            if (!action.getTemplatePool().isEmpty()) {
                showIndividualTemplateSettingsDialog(action, action.getTemplatePool().get(0));
            } else {
                showDetectLocationDialog(action);
            }
        });

        dialogView.findViewById(R.id.optImgTestAction).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            new Thread(() -> {
                try { Thread.sleep(120); } catch (Exception ignored) {}
                Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                new Handler(Looper.getMainLooper()).post(() -> {
                    openHudBar();
                    if (screen != null) {
                        Point matchCenter = findMatchCenterForAction(screen, action);
                        if (matchCenter != null) {
                            int clickTargetX = matchCenter.x + action.getOffsetX();
                            int clickTargetY = matchCenter.y + action.getOffsetY();
                            Action testAct = new Action("Click (x, y)", "", clickTargetX, clickTargetY);
                            testAct.setClickStyle(action.getClickStyle());
                            executeSingleActionNow(testAct);
                            Toast.makeText(FloatingWindowService.this, "تم رصد الهدف والنقر في مركزه فوراً!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(FloatingWindowService.this, "الهدف غير موجود على الشاشة وفق النسبة المحددة!", Toast.LENGTH_SHORT).show();
                        }
                        screen.recycle();
                    }
                });
            }).start();
        });

        dialogView.findViewById(R.id.optImgCopy).setOnClickListener(v -> {
            dialog.dismiss();
            GlobalData.actionList.add(index + 1, action.clone());
            updateHudActionCards();
            Toast.makeText(this, "تم نسخ الأكشن!", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.optImgDelete).setOnClickListener(v -> {
            dialog.dismiss();
            GlobalData.actionList.remove(index);
            updateHudActionCards();
            Toast.makeText(this, "تم حذف الأكشن!", Toast.LENGTH_SHORT).show();
        });

        TextView tvDisable = dialogView.findViewById(R.id.tvImgDisableText);
        if (tvDisable != null) tvDisable.setText(action.isDisabled() ? "Enable" : "Disable");
        dialogView.findViewById(R.id.optImgDisable).setOnClickListener(v -> {
            dialog.dismiss();
            action.setDisabled(!action.isDisabled());
            updateHudActionCards();
            Toast.makeText(this, action.isDisabled() ? "تم تعطيل الأكشن" : "تم تفعيل الأكشن", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showImageConditionSubMenu(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_image_condition_sub_menu, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        dialogView.findViewById(R.id.subOptChangeImage).setOnClickListener(v -> {
            dialog.dismiss();
            showImageSourcePicker(action, true);
        });

        dialogView.findViewById(R.id.subOptEditSimilarity).setOnClickListener(v -> {
            dialog.dismiss();
            showEditSimilarityDialog(action);
        });

        dialogView.findViewById(R.id.subOptEditDetectLocation).setOnClickListener(v -> {
            dialog.dismiss();
            showDetectLocationDialog(action);
        });

        dialogView.findViewById(R.id.subOptEditImage).setOnClickListener(v -> {
            dialog.dismiss();
            if (!action.getTemplatePool().isEmpty()) {
                showIndividualTemplateSettingsDialog(action, action.getTemplatePool().get(0));
            } else {
                showDetectLocationDialog(action);
            }
        });

        dialogView.findViewById(R.id.subOptTestCondition).setOnClickListener(v -> {
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
                            Point match = VisionEngine.findActionTarget(screen, template, action);
                            double exactSim = (match != null) ? VisionEngine.matchTemplateScore(screen, template, match.x, match.y, 30, false) : 0.0;
                            int recommended = MacroAiInspector.getRecommendedSimilarity(exactSim);
                            String availableRates = MacroAiInspector.generateAvailablePassingPercentages(exactSim);
                            showAiResultProDialog(action, exactSim, recommended, availableRates);
                            template.recycle();
                        }
                        screen.recycle();
                    } else {
                        Toast.makeText(FloatingWindowService.this, "تعذر التقاط الشاشة!", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        dialogView.findViewById(R.id.subOptCopy).setOnClickListener(v -> {
            dialog.dismiss();
            GlobalData.actionList.add(action.clone());
            updateHudActionCards();
            Toast.makeText(this, "تم نسخ الشرط!", Toast.LENGTH_SHORT).show();
        });

        TextView tvCount = dialogView.findViewById(R.id.tvAlternateTemplatesCount);
        if (tvCount != null) {
            tvCount.setText("Manage Templates (x" + action.getTemplatePool().size() + " Trained)");
        }
        dialogView.findViewById(R.id.subOptAddAlternateTemplate).setOnClickListener(v -> {
            dialog.dismiss();
            showTemplateManagerDialog(action);
        });

        dialog.show();
    }

    private void showTemplateManagerDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_template_manager, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        TextView tvHeader = dialogView.findViewById(R.id.tvManagerHeaderTitle);
        tvHeader.setText("Templates (" + action.getTemplatePool().size() + " Trained)");

        LinearLayout listContainer = dialogView.findViewById(R.id.layoutTemplatesListContainer);
        listContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(themedContext);

        List<TemplateItem> pool = action.getTemplatePool();
        for (int i = 0; i < pool.size(); i++) {
            final int itemIndex = i;
            final TemplateItem item = pool.get(i);

            View card = inflater.inflate(R.layout.item_exist_image_card, listContainer, false);
            ImageView ivThumb = card.findViewById(R.id.ivExistThumbPreview);
            TextView tvName = card.findViewById(R.id.tvExistImageName);
            TextView tvDim = card.findViewById(R.id.tvExistImageDimensions);
            ImageView btnDelete = card.findViewById(R.id.btnDeleteTemplateItem);

            Bitmap bmp = BitmapFactory.decodeFile(item.getImagePath());
            if (bmp != null) {
                ivThumb.setImageBitmap(bmp);
                tvDim.setText("[" + bmp.getWidth() + "x" + bmp.getHeight() + "px] • Sim: " + item.getSimilarity() + "%");
            }
            tvName.setText((itemIndex == 0 ? "★ Primary: " : "Alt " + itemIndex + ": ") + item.getName());

            card.setOnClickListener(v -> {
                dialog.dismiss();
                showIndividualTemplateSettingsDialog(action, item);
            });

            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    if (pool.size() <= 1) {
                        Toast.makeText(this, "لا يمكن حذف القالب الأساسي الوحيد!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pool.remove(itemIndex);
                    updateHudActionCards();
                    dialog.dismiss();
                    showTemplateManagerDialog(action);
                    Toast.makeText(this, "تم حذف القالب بنجاح!", Toast.LENGTH_SHORT).show();
                });
            }

            listContainer.addView(card);
        }

        dialogView.findViewById(R.id.btnAddNewTrainedTemplate).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            currentTargetForAlternateTemplate = action;
            Intent intent = new Intent(this, ImageCropPickerService.class);
            intent.putExtra("is_alternate_template", true);
            startService(intent);
        });

        dialogView.findViewById(R.id.btnCloseTemplateManager).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showIndividualTemplateSettingsDialog(Action action, TemplateItem item) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_template_settings, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        EditText etCustomName = dialogView.findViewById(R.id.etTemplateCustomName);
        etCustomName.setText(item.getName());

        ImageView ivLivePreview = dialogView.findViewById(R.id.ivSettingsTemplatePreview);
        TextView tvLiveDim = dialogView.findViewById(R.id.tvLiveTrimDimensions);

        final File templateFile = new File(item.getImagePath());
        final Bitmap masterBmp = BitmapFactory.decodeFile(templateFile.getAbsolutePath());
        final int[] trimOffsets = {0, 0};

        if (masterBmp != null) {
            ivLivePreview.setImageBitmap(masterBmp);
            tvLiveDim.setText("[" + masterBmp.getWidth() + " x " + masterBmp.getHeight() + " px]");
        }

        EditText etSim = dialogView.findViewById(R.id.etTemplateSim);
        etSim.setText(String.valueOf(item.getSimilarity()));

        EditText etTol = dialogView.findViewById(R.id.etTemplateTolerance);
        etTol.setText(String.valueOf(item.getColorTolerance()));

        Switch swLevel = dialogView.findViewById(R.id.swIgnoreLevelBadge);
        swLevel.setChecked(item.isIgnoreLevelBadge());

        Switch swAnchor = dialogView.findViewById(R.id.swEnableAnchor);
        swAnchor.setChecked(item.hasAnchor());

        ImageView ivAnchorPreview = dialogView.findViewById(R.id.ivAnchorThumbPreview);
        TextView tvAnchorPlaceholder = dialogView.findViewById(R.id.tvAnchorEmptyPlaceholder);
        TextView tvAnchorBadge = dialogView.findViewById(R.id.tvAnchorOffsetBadge);
        EditText etAnchorSim = dialogView.findViewById(R.id.etAnchorSim);
        EditText etAnchorTol = dialogView.findViewById(R.id.etAnchorTolerance);

        etAnchorSim.setText(String.valueOf(item.getAnchorSimilarity()));
        etAnchorTol.setText(String.valueOf(item.getAnchorTolerance()));

        if (item.hasAnchor() && item.getAnchorImagePath() != null) {
            Bitmap aBmp = BitmapFactory.decodeFile(item.getAnchorImagePath());
            if (aBmp != null) {
                ivAnchorPreview.setImageBitmap(aBmp);
                tvAnchorPlaceholder.setVisibility(View.GONE);
                tvAnchorBadge.setVisibility(View.VISIBLE);
                tvAnchorBadge.setText("[Offset: " + item.getAnchorRelativeX() + ", " + item.getAnchorRelativeY() + "]");
            }
        }

        dialogView.findViewById(R.id.btnCaptureLandmarkAnchor).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            currentEditingAnchorItem = item;
            Intent intent = new Intent(this, ImageCropPickerService.class);
            intent.putExtra("is_anchor_capture", true);
            intent.putExtra("parent_x", action.getX());
            intent.putExtra("parent_y", action.getY());
            startService(intent);
        });

        dialogView.findViewById(R.id.btnDeleteAnchor).setOnClickListener(v -> {
            item.setHasAnchor(false);
            item.setAnchorImagePath(null);
            swAnchor.setChecked(false);
            ivAnchorPreview.setImageBitmap(null);
            tvAnchorPlaceholder.setVisibility(View.VISIBLE);
            tvAnchorBadge.setVisibility(View.GONE);
            Toast.makeText(this, "تم حذف العلامة المرجعية", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnSaveTemplateSettings).setOnClickListener(v -> {
            String newName = etCustomName.getText().toString().trim();
            if (!newName.isEmpty()) item.setName(newName);

            String sVal = etSim.getText().toString();
            String tVal = etTol.getText().toString();
            int sim = sVal.isEmpty() ? 70 : Integer.parseInt(sVal);
            int tol = tVal.isEmpty() ? 30 : Integer.parseInt(tVal);

            item.setSimilarity(Math.max(1, Math.min(100, sim)));
            item.setColorTolerance(Math.max(10, Math.min(80, tol)));
            item.setIgnoreLevelBadge(swLevel.isChecked());

            item.setHasAnchor(swAnchor.isChecked());
            String aSimVal = etAnchorSim.getText().toString();
            String aTolVal = etAnchorTol.getText().toString();
            item.setAnchorSimilarity(aSimVal.isEmpty() ? 75 : Integer.parseInt(aSimVal));
            item.setAnchorTolerance(aTolVal.isEmpty() ? 35 : Integer.parseInt(aTolVal));

            if (action.getTemplatePool().indexOf(item) == 0) {
                action.setSimilarity(item.getSimilarity());
            }

            updateHudActionCards();
            dialog.dismiss();
            Toast.makeText(this, "تم حفظ الإعدادات بنجاح!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    /**
     * إيجاد موضع الهدف الحقيقي الفوري بدون أي تأخير
     */
    private Point findMatchCenterForAction(Bitmap screen, Action action) {
        if (screen == null || action == null) return null;

        List<TemplateItem> pool = action.getTemplatePool();
        for (TemplateItem item : pool) {
            Bitmap tBmp = BitmapFactory.decodeFile(item.getImagePath());
            if (tBmp == null) continue;

            Point found = VisionEngine.findActionTarget(screen, tBmp, action);
            int tw = tBmp.getWidth();
            int th = tBmp.getHeight();
            tBmp.recycle();

            if (found != null) {
                // التحقق من العلامة المرجعية إذا كانت نشطة
                if (item.hasAnchor() && item.getAnchorImagePath() != null) {
                    Bitmap aBmp = BitmapFactory.decodeFile(item.getAnchorImagePath());
                    if (aBmp != null) {
                        int expAnchorX = found.x + item.getAnchorRelativeX();
                        int expAnchorY = found.y + item.getAnchorRelativeY();
                        Point anchorFound = VisionEngine.scanAndFindTemplate(
                                screen, aBmp,
                                Math.max(0, expAnchorX - 25), Math.max(0, expAnchorY - 25),
                                aBmp.getWidth() + 50, aBmp.getHeight() + 50,
                                expAnchorX, expAnchorY,
                                item.getAnchorSimilarity(), item.getAnchorTolerance(), false
                        );
                        aBmp.recycle();
                        if (anchorFound == null) continue;
                    }
                }
                return new Point(found.x + (tw / 2), found.y + (th / 2));
            }
        }
        return null;
    }

    private void showEditOffsetDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Edit Offset");

        LinearLayout layout = new LinearLayout(themedContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        TextView tvX = new TextView(themedContext);
        tvX.setText("Offset X (px):");
        tvX.setTextColor(android.graphics.Color.WHITE);
        final EditText etX = new EditText(themedContext);
        etX.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etX.setText(String.valueOf(action.getOffsetX()));

        TextView tvY = new TextView(themedContext);
        tvY.setText("Offset Y (px):");
        tvY.setTextColor(android.graphics.Color.WHITE);
        tvY.setPadding(0, 16, 0, 0);
        final EditText etY = new EditText(themedContext);
        etY.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etY.setText(String.valueOf(action.getOffsetY()));

        layout.addView(tvX); layout.addView(etX);
        layout.addView(tvY); layout.addView(etY);
        builder.setView(layout);

        builder.setPositiveButton("حفظ", (d, w) -> {
            String sx = etX.getText().toString();
            String sy = etY.getText().toString();
            action.setOffsetX(sx.isEmpty() ? 0 : Integer.parseInt(sx));
            action.setOffsetY(sy.isEmpty() ? 0 : Integer.parseInt(sy));
            updateHudActionCards();
        });
        builder.setNegativeButton("إلغاء", null);

        AlertDialog dialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.show();
    }

    private void showImageSourcePicker(@Nullable Action actionToEdit, boolean isEditingExisting) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        View dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_image_source_picker, null);

        AlertDialog dialog = new AlertDialog.Builder(themedContext)
                .setView(dialogView)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        dialogView.findViewById(R.id.btnSourceNew).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            Intent intent = new Intent(this, ImageCropPickerService.class);
            intent.putExtra("is_click_image", true);
            startService(intent);
        });

        dialog.show();
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
        });

        dialogView.findViewById(R.id.btnImageAppear).setOnClickListener(v -> {
            dialog.dismiss();
            closeHudBar();
            Intent intent = new Intent(this, ImageCropPickerService.class);
            intent.putExtra("target_x", x);
            intent.putExtra("target_y", y);
            intent.putExtra("is_click_image", false);
            startService(intent);
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
            tvStatus.setText("✅ تم رصد الهدف بنجاح!");
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
            if (!action.getTemplatePool().isEmpty()) {
                action.getTemplatePool().get(0).setSimilarity(recommendedSim);
            }
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
            if (!action.getTemplatePool().isEmpty()) {
                action.getTemplatePool().get(0).setSimilarity(sim);
            }
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
            intent.putExtra("edit_index", index);
            intent.putExtra("init_x", action.getX());
            intent.putExtra("init_y", action.getY());
            startService(intent);
        });

        dialogView.findViewById(R.id.optEditScaling).setOnClickListener(v -> {
            dialog.dismiss();
            showScalingAlgorithmDialog(action);
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
            Toast.makeText(this, "تم اختبار الأكشن فوراً!", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.optAddCondition).setOnClickListener(v -> {
            dialog.dismiss();
            showConditionDialog(action.getX(), action.getY());
        });

        dialogView.findViewById(R.id.optCopy).setOnClickListener(v -> {
            dialog.dismiss();
            inAppClipboardAction = action.clone();
            GlobalData.actionList.add(index + 1, action.clone());
            updateHudActionCards();
        });

        dialogView.findViewById(R.id.optDelete).setOnClickListener(v -> {
            dialog.dismiss();
            GlobalData.actionList.remove(index);
            updateHudActionCards();
        });

        dialog.show();
    }

    private void showScalingAlgorithmDialog(Action action) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("Scaling Algorithm");
        String[] algorithms = {"Aspect Ratio", "Center Anchor", "Top-Left Anchor", "Absolute Pixels"};
        builder.setItems(algorithms, (d, which) -> {
            action.setScalingAlgorithm(algorithms[which].split(" ")[0]);
            updateHudActionCards();
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
        builder.setTitle("Click Style");
        String[] styles = {"Single Click", "Double Click", "Triple Click", "Long Click"};

        builder.setItems(styles, (d, which) -> {
            action.setClickStyle(styles[which]);
            updateHudActionCards();
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
        builder.setTitle("Edit Delay");

        LinearLayout layout = new LinearLayout(themedContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        TextView tvBefore = new TextView(themedContext);
        tvBefore.setText("Wait Before (ms):");
        tvBefore.setTextColor(android.graphics.Color.WHITE);
        final EditText etBefore = new EditText(themedContext);
        etBefore.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etBefore.setText(String.valueOf(action.getDelayBeforeMs()));

        TextView tvAfter = new TextView(themedContext);
        tvAfter.setText("Wait After (ms):");
        tvAfter.setTextColor(android.graphics.Color.WHITE);
        tvAfter.setPadding(0, 16, 0, 0);
        final EditText etAfter = new EditText(themedContext);
        etAfter.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etAfter.setText(String.valueOf(action.getDelayAfterMs()));

        layout.addView(tvBefore); layout.addView(etBefore);
        layout.addView(tvAfter); layout.addView(etAfter);
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

    private void setupHudButtons() {
        View btnCollapse = hudBarView.findViewById(R.id.btnCollapse);
        if (btnCollapse != null) btnCollapse.setOnClickListener(v -> closeHudBar());

        View btnPlay = hudBarView.findViewById(R.id.btnPlayHud);
        if (btnPlay != null) btnPlay.setOnClickListener(v -> {
            closeHudBar();
            startMacroLoopExecution();
        });

        View btnAdd = hudBarView.findViewById(R.id.btnAddActionHud);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> showFullActionDialog());

        View btnVar = hudBarView.findViewById(R.id.btnVariables);
        if (btnVar != null) btnVar.setOnClickListener(v -> showAiDiagnosticsDialog());
    }

    /**
     * تشغيل الماكرو الفوري دون أي بطء أو تجميد
     */
    private void startMacroLoopExecution() {
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
        if (floatingView != null) floatingView.setVisibility(View.GONE);

        showStopButtonOverlay();

        isMacroRunning = true;
        Toast.makeText(this, "بدء تشغيل الماكرو فائق السرعة...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try { Thread.sleep(60); } catch (InterruptedException ignored) {}

            while (isMacroRunning) {
                for (int i = 0; i < GlobalData.actionList.size(); i++) {
                    if (!isMacroRunning) break;

                    Action action = GlobalData.actionList.get(i);
                    if (action.isDisabled()) continue;

                    if (action.getDelayBeforeMs() > 0) {
                        try { Thread.sleep(action.getDelayBeforeMs()); } catch (InterruptedException ignored) {}
                    }
                    if (!isMacroRunning) break;

                    if (AutoAccessibilityService.instance != null) {
                        String type = action.getType();

                        // 1. أكشن Click Image
                        if ("Click Image".equals(type)) {
                            Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                            if (screen != null) {
                                Point matchCenter = findMatchCenterForAction(screen, action);
                                if (matchCenter != null) {
                                    int clickTargetX = matchCenter.x + action.getOffsetX();
                                    int clickTargetY = matchCenter.y + action.getOffsetY();
                                    Action clickAct = new Action("Click (x, y)", "", clickTargetX, clickTargetY);
                                    clickAct.setClickStyle(action.getClickStyle());
                                    executeSingleActionNow(clickAct);
                                }
                                screen.recycle();
                            }
                        } 
                        // 2. أكشن Click (x, y) مع شرط الصورة
                        else if ("Click (x, y)".equals(type)) {
                            if (action.hasCondition() && "Image Appear".equals(action.getConditionType())) {
                                boolean conditionMet = false;
                                Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                                if (screen != null) {
                                    Point matchCenter = findMatchCenterForAction(screen, action);
                                    conditionMet = (matchCenter != null);
                                    if (action.isNotAppear()) conditionMet = !conditionMet;
                                    screen.recycle();
                                }
                                if (!conditionMet) continue;
                            }

                            int targetX = action.getX() + action.getOffsetX();
                            int targetY = action.getY() + action.getOffsetY();
                            Action offsetAct = action.clone();
                            offsetAct.setX(targetX);
                            offsetAct.setY(targetY);
                            executeSingleActionNow(offsetAct);

                        } else if ("Swipe".equals(type)) {
                            AutoAccessibilityService.instance.swipe(500, 1200, 500, 400, 400);
                        } else if ("Press Back".equals(type)) {
                            AutoAccessibilityService.instance.pressBack();
                        } else if ("Press Home".equals(type)) {
                            AutoAccessibilityService.instance.pressHome();
                        } else if ("Wait".equals(type)) {
                            try { Thread.sleep(action.getDelayAfterMs()); } catch (InterruptedException ignored) {}
                        } else if ("Stop Macro".equals(type)) {
                            new Handler(Looper.getMainLooper()).post(this::stopMacroExecution);
                            break;
                        }
                    }

                    if (action.getDelayAfterMs() > 0) {
                        try { Thread.sleep(action.getDelayAfterMs()); } catch (InterruptedException ignored) {}
                    }
                }
            }
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

        dialogView.findViewById(R.id.btnClickImage).setOnClickListener(v -> {
            dialog.dismiss();
            showImageSourcePicker(null, false);
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
                .setContentTitle("Auto Engine")
                .setContentText("محرك الأتمتة السريع نشط")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isMacroRunning = false;
        if (coordReceiver != null) try { unregisterReceiver(coordReceiver); } catch (Exception ignored) {}
        if (cropReceiver != null) try { unregisterReceiver(cropReceiver); } catch (Exception ignored) {}
        if (clickImageReceiver != null) try { unregisterReceiver(clickImageReceiver); } catch (Exception ignored) {}
        if (customRegionReceiver != null) try { unregisterReceiver(customRegionReceiver); } catch (Exception ignored) {}
        if (alternateTemplateReceiver != null) try { unregisterReceiver(alternateTemplateReceiver); } catch (Exception ignored) {}
        if (anchorReceiver != null) try { unregisterReceiver(anchorReceiver); } catch (Exception ignored) {}
        if (floatingView != null && windowManager != null) try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
        if (popupMenuView != null && windowManager != null) try { windowManager.removeView(popupMenuView); } catch (Exception ignored) {}
        if (hudBarView != null && windowManager != null) try { windowManager.removeView(hudBarView); } catch (Exception ignored) {}
        if (stopButtonView != null && windowManager != null && isStopButtonVisible) try { windowManager.removeView(stopButtonView); } catch (Exception ignored) {}
    }
}
