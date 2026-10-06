package com.example.myautomationapp;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.Nullable;

public class CoordinatePickerService extends Service {
    private WindowManager windowManager;
    private View pickerView;
    private WindowManager.LayoutParams params;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        pickerView = LayoutInflater.from(this).inflate(R.layout.picker_layout, null);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 500;
        params.y = 1000;

        windowManager.addView(pickerView, params);
        setupTouch();
        setupButtons();
    }

    private void setupTouch() {
        View circle = pickerView.findViewById(R.id.pickerCircle);
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
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(pickerView, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void setupButtons() {
        // زر التأكيد
        pickerView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            Intent intent = new Intent("COORDINATES_PICKED");
            intent.putExtra("x", params.x);
            intent.putExtra("y", params.y);
            sendBroadcast(intent);
            stopSelf();
        });

        // زر الإلغاء
        pickerView.findViewById(R.id.btnCancel).setOnClickListener(v -> stopSelf());

        // زر النسخ
        pickerView.findViewById(R.id.btnCopy).setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Coords", "X: " + params.x + " Y: " + params.y);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "تم نسخ الإحداثيات", Toast.LENGTH_SHORT).show();
            }
        });

        // زر إعادة التمركز
        pickerView.findViewById(R.id.btnCenter).setOnClickListener(v -> {
            params.x = 500; // منتصف الشاشة تقريباً
            params.y = 1000;
            windowManager.updateViewLayout(pickerView, params);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pickerView != null) windowManager.removeView(pickerView);
    }
}
