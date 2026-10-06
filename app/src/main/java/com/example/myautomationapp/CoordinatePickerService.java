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
    private View pickerView;
    private WindowManager.LayoutParams params;
    private TextView tvLiveCoords;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        pickerView = LayoutInflater.from(themedContext).inflate(R.layout.picker_layout, null);
        tvLiveCoords = pickerView.findViewById(R.id.tvLiveCoords);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 400;
        params.y = 800;

        try {
            windowManager.addView(pickerView, params);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
            return;
        }

        updateCoordText();
        setupTouch();
        setupButtons();
    }

    private void updateCoordText() {
        if (tvLiveCoords != null) {
            int centerX = getCalculatedCenterX();
            int centerY = getCalculatedCenterY();
            tvLiveCoords.setText("X: " + centerX + "  Y: " + centerY);
        }
    }

    private int getCalculatedCenterX() {
        View circle = pickerView.findViewById(R.id.pickerCircle);
        return params.x + (circle != null ? (circle.getLeft() + circle.getWidth() / 2) : 110);
    }

    private int getCalculatedCenterY() {
        View circle = pickerView.findViewById(R.id.pickerCircle);
        return params.y + (circle != null ? (circle.getTop() + circle.getHeight() / 2) : 150);
    }

    private void setupTouch() {
        View circle = pickerView.findViewById(R.id.pickerCircle);
        if (circle == null) return;

        circle.setOnTouchListener(new View.OnTouchListener() {
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
                        int targetX = initialX + (int) (event.getRawX() - initialTouchX);
                        int targetY = initialY + (int) (event.getRawY() - initialTouchY);

                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        int w = pickerView.getWidth() > 0 ? pickerView.getWidth() : 220;
                        int h = pickerView.getHeight() > 0 ? pickerView.getHeight() : 200;

                        if (targetX < 0) targetX = 0;
                        if (targetX > dm.widthPixels - w) targetX = dm.widthPixels - w;
                        if (targetY < 60) targetY = 60;
                        if (targetY > dm.heightPixels - h - 60) targetY = dm.heightPixels - h - 60;

                        params.x = targetX;
                        params.y = targetY;
                        windowManager.updateViewLayout(pickerView, params);
                        updateCoordText();
                        return true;
                }
                return false;
            }
        });
    }

    private void setupButtons() {
        pickerView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            int centerX = getCalculatedCenterX();
            int centerY = getCalculatedCenterY();

            Intent intent = new Intent("COORDINATES_PICKED");
            intent.setPackage(getPackageName());
            intent.putExtra("x", centerX);
            intent.putExtra("y", centerY);
            sendBroadcast(intent);
            stopSelf();
        });

        pickerView.findViewById(R.id.btnCancel).setOnClickListener(v -> stopSelf());

        pickerView.findViewById(R.id.btnCopy).setOnClickListener(v -> {
            int cx = getCalculatedCenterX();
            int cy = getCalculatedCenterY();
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Coords", cx + ", " + cy);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "تم نسخ الإحداثيات: " + cx + ", " + cy, Toast.LENGTH_SHORT).show();
            }
        });

        pickerView.findViewById(R.id.btnCenter).setOnClickListener(v -> {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            params.x = (dm.widthPixels / 2) - (pickerView.getWidth() / 2);
            params.y = (dm.heightPixels / 2) - (pickerView.getHeight() / 2);
            windowManager.updateViewLayout(pickerView, params);
            updateCoordText();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pickerView != null && windowManager != null) {
            try { windowManager.removeView(pickerView); } catch (Exception ignored) {}
        }
    }
}
