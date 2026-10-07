package com.example.myautomationapp;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;

public class CoordinatePickerService extends Service {
    private WindowManager windowManager;
    private View controlBoxView;
    private View circleTargetView;
    private WindowManager.LayoutParams boxParams;
    private WindowManager.LayoutParams circleParams;
    private TextView tvLiveCoords;
    private int circleSizePx;
    private int editActionIndex = -1; // إذا كان في وضع تعديل إحداثيات أكشن موجود

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("edit_index")) {
            editActionIndex = intent.getIntExtra("edit_index", -1);
            int initX = intent.getIntExtra("init_x", -1);
            int initY = intent.getIntExtra("init_y", -1);
            if (initX >= 0 && initY >= 0 && circleParams != null) {
                circleParams.x = Math.max(0, initX - (circleSizePx / 2));
                circleParams.y = Math.max(0, initY - (circleSizePx / 2));
                if (windowManager != null && circleTargetView != null) {
                    windowManager.updateViewLayout(circleTargetView, circleParams);
                    circleTargetView.post(this::updateLiveCoords);
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        circleSizePx = (int) (48 * dm.density);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE 
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN 
                  | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        controlBoxView = LayoutInflater.from(themedContext).inflate(R.layout.picker_layout, null);
        tvLiveCoords = controlBoxView.findViewById(R.id.tvLiveCoords);
        boxParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        boxParams.gravity = Gravity.TOP | Gravity.START;
        boxParams.x = (dm.widthPixels / 2) - (int) (115 * dm.density);
        boxParams.y = (int) (100 * dm.density);

        circleTargetView = LayoutInflater.from(themedContext).inflate(R.layout.picker_circle_layout, null);
        circleParams = new WindowManager.LayoutParams(
                circleSizePx, circleSizePx,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        circleParams.gravity = Gravity.TOP | Gravity.START;
        circleParams.x = (dm.widthPixels / 2) - (circleSizePx / 2);
        circleParams.y = (dm.heightPixels / 2) - (circleSizePx / 2);

        try {
            windowManager.addView(controlBoxView, boxParams);
            windowManager.addView(circleTargetView, circleParams);
        } catch (Exception e) {
            stopSelf();
            return;
        }

        circleTargetView.post(this::updateLiveCoords);
        setupCircleDrag();
        setupBoxDrag();
        setupBoxButtons();
    }

    private int getExactCenterX() {
        if (circleTargetView == null) return circleParams.x + (circleSizePx / 2);
        int[] loc = new int[2];
        circleTargetView.getLocationOnScreen(loc);
        int w = circleTargetView.getWidth() > 0 ? circleTargetView.getWidth() : circleSizePx;
        return loc[0] + (w / 2);
    }

    private int getExactCenterY() {
        if (circleTargetView == null) return circleParams.y + (circleSizePx / 2);
        int[] loc = new int[2];
        circleTargetView.getLocationOnScreen(loc);
        int h = circleTargetView.getHeight() > 0 ? circleTargetView.getHeight() : circleSizePx;
        return loc[1] + (h / 2);
    }

    private void updateLiveCoords() {
        if (tvLiveCoords != null) {
            tvLiveCoords.setText("X: " + getExactCenterX() + "  Y: " + getExactCenterY());
        }
    }

    private void setupCircleDrag() {
        circleTargetView.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = circleParams.x; initY = circleParams.y;
                        touchX = event.getRawX(); touchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int targetX = initX + (int) (event.getRawX() - touchX);
                        int targetY = initY + (int) (event.getRawY() - touchY);

                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        if (targetX < 0) targetX = 0;
                        if (targetX > dm.widthPixels - circleSizePx) targetX = dm.widthPixels - circleSizePx;
                        if (targetY < 0) targetY = 0;
                        if (targetY > dm.heightPixels - circleSizePx) targetY = dm.heightPixels - circleSizePx;

                        circleParams.x = targetX;
                        circleParams.y = targetY;
                        windowManager.updateViewLayout(circleTargetView, circleParams);
                        circleTargetView.post(CoordinatePickerService.this::updateLiveCoords);
                        return true;
                }
                return false;
            }
        });
    }

    private void setupBoxDrag() {
        controlBoxView.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = boxParams.x; initY = boxParams.y;
                        touchX = event.getRawX(); touchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int targetX = initX + (int) (event.getRawX() - touchX);
                        int targetY = initY + (int) (event.getRawY() - touchY);

                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        int bw = controlBoxView.getWidth() > 0 ? controlBoxView.getWidth() : 230;
                        if (targetX < 0) targetX = 0;
                        if (targetX > dm.widthPixels - bw) targetX = dm.widthPixels - bw;
                        if (targetY < 40) targetY = 40;
                        if (targetY > dm.heightPixels - 120) targetY = dm.heightPixels - 120;

                        boxParams.x = targetX;
                        boxParams.y = targetY;
                        windowManager.updateViewLayout(controlBoxView, boxParams);
                        return true;
                }
                return false;
            }
        });
    }

    private void setupBoxButtons() {
        controlBoxView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            int cx = getExactCenterX();
            int cy = getExactCenterY();

            Intent intent = new Intent("COORDINATES_PICKED");
            intent.setPackage(getPackageName());
            intent.putExtra("x", cx);
            intent.putExtra("y", cy);
            intent.putExtra("edit_index", editActionIndex); // تمرير رقم الأكشن المعدل
            sendBroadcast(intent);
            stopSelf();
        });

        controlBoxView.findViewById(R.id.btnCancel).setOnClickListener(v -> stopSelf());

        controlBoxView.findViewById(R.id.btnCopy).setOnClickListener(v -> {
            int cx = getExactCenterX();
            int cy = getExactCenterY();
            android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Coords", cx + ", " + cy);
            if (cb != null) {
                cb.setPrimaryClip(clip);
                Toast.makeText(this, "تم نسخ الإحداثيات: " + cx + ", " + cy, Toast.LENGTH_SHORT).show();
            }
        });

        controlBoxView.findViewById(R.id.btnCenter).setOnClickListener(v -> {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            circleParams.x = (dm.widthPixels / 2) - (circleSizePx / 2);
            circleParams.y = (dm.heightPixels / 2) - (circleSizePx / 2);
            windowManager.updateViewLayout(circleTargetView, circleParams);
            circleTargetView.post(CoordinatePickerService.this::updateLiveCoords);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controlBoxView != null && windowManager != null) {
            try { windowManager.removeView(controlBoxView); } catch (Exception ignored) {}
        }
        if (circleTargetView != null && windowManager != null) {
            try { windowManager.removeView(circleTargetView); } catch (Exception ignored) {}
        }
    }
}
