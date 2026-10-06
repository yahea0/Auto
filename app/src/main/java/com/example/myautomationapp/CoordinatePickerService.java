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
    private View controlBoxView;   // صندوق الإحداثيات والأزرار
    private View circleTargetView; // دائرة التصويب المستقلة الحرة
    
    private WindowManager.LayoutParams boxParams;
    private WindowManager.LayoutParams circleParams;
    private TextView tvLiveCoords;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        // 1. إعداد صندوق الإحداثيات في أعلى الشاشة (مستقل)
        controlBoxView = LayoutInflater.from(themedContext).inflate(R.layout.picker_layout, null);
        tvLiveCoords = controlBoxView.findViewById(R.id.tvLiveCoords);
        boxParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        boxParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        boxParams.y = 100;

        // 2. إعداد دائرة التصويب الصغيرة الحرة (46dp) في منتصف الشاشة
        circleTargetView = LayoutInflater.from(themedContext).inflate(R.layout.picker_circle_layout, null);
        circleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        circleParams.gravity = Gravity.TOP | Gravity.START;
        circleParams.x = 500;
        circleParams.y = 900;

        try {
            windowManager.addView(controlBoxView, boxParams);
            windowManager.addView(circleTargetView, circleParams);
        } catch (Exception e) {
            stopSelf();
            return;
        }

        updateLiveCoords();
        setupCircleDrag();
        setupBoxButtons();
    }

    private int getExactCenterX() {
        int w = circleTargetView.getWidth() > 0 ? circleTargetView.getWidth() : 120;
        return circleParams.x + (w / 2);
    }

    private int getExactCenterY() {
        int h = circleTargetView.getHeight() > 0 ? circleTargetView.getHeight() : 120;
        return circleParams.y + (h / 2);
    }

    private void updateLiveCoords() {
        if (tvLiveCoords != null) {
            tvLiveCoords.setText("X: " + getExactCenterX() + "  Y: " + getExactCenterY());
        }
    }

    // سحب دائرة التصويب بحرية تامة دون أي عوائق
    private void setupCircleDrag() {
        circleTargetView.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = circleParams.x;
                        initY = circleParams.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int targetX = initX + (int) (event.getRawX() - touchX);
                        int targetY = initY + (int) (event.getRawY() - touchY);

                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        int cw = circleTargetView.getWidth() > 0 ? circleTargetView.getWidth() : 120;
                        int ch = circleTargetView.getHeight() > 0 ? circleTargetView.getHeight() : 120;

                        if (targetX < 0) targetX = 0;
                        if (targetX > dm.widthPixels - cw) targetX = dm.widthPixels - cw;
                        if (targetY < 40) targetY = 40;
                        if (targetY > dm.heightPixels - ch - 40) targetY = dm.heightPixels - ch - 40;

                        circleParams.x = targetX;
                        circleParams.y = targetY;
                        windowManager.updateViewLayout(circleTargetView, circleParams);
                        updateLiveCoords();
                        return true;
                }
                return false;
            }
        });
    }

    private void setupBoxButtons() {
        // تأكيد النقطة وإرسالها
        controlBoxView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            int cx = getExactCenterX();
            int cy = getExactCenterY();

            Intent intent = new Intent("COORDINATES_PICKED");
            intent.setPackage(getPackageName());
            intent.putExtra("x", cx);
            intent.putExtra("y", cy);
            sendBroadcast(intent);
            stopSelf();
        });

        // إلغاء
        controlBoxView.findViewById(R.id.btnCancel).setOnClickListener(v -> stopSelf());

        // نسخ
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

        // إعادة التمركز في منتصف الشاشة
        controlBoxView.findViewById(R.id.btnCenter).setOnClickListener(v -> {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            circleParams.x = (dm.widthPixels / 2) - (circleTargetView.getWidth() / 2);
            circleParams.y = (dm.heightPixels / 2) - (circleTargetView.getHeight() / 2);
            windowManager.updateViewLayout(circleTargetView, circleParams);
            updateLiveCoords();
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
