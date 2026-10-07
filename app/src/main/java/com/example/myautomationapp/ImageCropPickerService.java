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
import androidx.annotation.Nullable;

public class ImageCropPickerService extends Service {
    private WindowManager windowManager;
    private View controlBoxView;
    private View cropFrameView;
    
    private WindowManager.LayoutParams boxParams;
    private WindowManager.LayoutParams frameParams;
    
    private TextView tvCropCoords, tvCropDimensions;
    private int targetClickX, targetClickY;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            targetClickX = intent.getIntExtra("target_x", 500);
            targetClickY = intent.getIntExtra("target_y", 1000);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_MyAutomationApp);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE 
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN 
                  | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        // 1. صندوق الإحداثيات والأبعاد
        controlBoxView = LayoutInflater.from(themedContext).inflate(R.layout.crop_picker_box_layout, null);
        tvCropCoords = controlBoxView.findViewById(R.id.tvCropCoords);
        tvCropDimensions = controlBoxView.findViewById(R.id.tvCropDimensions);

        boxParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        boxParams.gravity = Gravity.TOP | Gravity.START;
        boxParams.x = (dm.widthPixels / 2) - (int) (110 * dm.density);
        boxParams.y = (int) (60 * dm.density);

        // 2. مستطيل التحديد المطاطي (الافتراضي: 350x200 بكسل تقريباً)
        cropFrameView = LayoutInflater.from(themedContext).inflate(R.layout.crop_frame_layout, null);
        int initW = (int) (160 * dm.density);
        int initH = (int) (90 * dm.density);

        frameParams = new WindowManager.LayoutParams(
                initW, initH,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        frameParams.gravity = Gravity.TOP | Gravity.START;
        frameParams.x = (dm.widthPixels / 2) - (initW / 2);
        frameParams.y = (dm.heightPixels / 2) - (initH / 2);

        try {
            windowManager.addView(controlBoxView, boxParams);
            windowManager.addView(cropFrameView, frameParams);
        } catch (Exception e) {
            stopSelf();
            return;
        }

        updateLabels();
        setupCropInteractions();
        setupBoxInteractions();
    }

    private void updateLabels() {
        if (tvCropCoords != null) tvCropCoords.setText("X: " + frameParams.x + "  Y: " + frameParams.y);
        if (tvCropDimensions != null) tvCropDimensions.setText("W: " + frameParams.width + "  H: " + frameParams.height);
    }

    private void setupCropInteractions() {
        cropFrameView.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY, initW, initH;
            private float touchX, touchY;
            private boolean isResizing = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = frameParams.x;
                        initY = frameParams.y;
                        initW = frameParams.width;
                        initH = frameParams.height;
                        touchX = event.getRawX();
                        touchY = event.getRawY();

                        // إذا تم لمس الزاوية السفلية اليمنى (مقبض المثلث) -> تفعيل وضع التكبير والتصغير
                        isResizing = (event.getX() > frameParams.width - 90 && event.getY() > frameParams.height - 90);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchX;
                        float dy = event.getRawY() - touchY;

                        if (isResizing) {
                            // تكبير وتصغير الأبعاد بسلاسة
                            int newW = Math.max((int) (60 * getResources().getDisplayMetrics().density), initW + (int) dx);
                            int newH = Math.max((int) (40 * getResources().getDisplayMetrics().density), initH + (int) dy);
                            frameParams.width = newW;
                            frameParams.height = newH;
                        } else {
                            // تحريك المستطيل في أي اتجاه
                            frameParams.x = initX + (int) dx;
                            frameParams.y = initY + (int) dy;
                        }

                        windowManager.updateViewLayout(cropFrameView, frameParams);
                        updateLabels();
                        return true;
                }
                return false;
            }
        });
    }

    private void setupBoxInteractions() {
        // سحب صندوق التحكم
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
                        boxParams.x = initX + (int) (event.getRawX() - touchX);
                        boxParams.y = initY + (int) (event.getRawY() - touchY);
                        windowManager.updateViewLayout(controlBoxView, boxParams);
                        return true;
                }
                return false;
            }
        });

        // تأكيد القص وحفظ الصورة كشرط للأكشن
        controlBoxView.findViewById(R.id.btnCropConfirm).setOnClickListener(v -> {
            Intent intent = new Intent("IMAGE_TEMPLATE_CROPPED");
            intent.setPackage(getPackageName());
            intent.putExtra("crop_x", frameParams.x);
            intent.putExtra("crop_y", frameParams.y);
            intent.putExtra("crop_w", frameParams.width);
            intent.putExtra("crop_h", frameParams.height);
            intent.putExtra("target_x", targetClickX);
            intent.putExtra("target_y", targetClickY);
            sendBroadcast(intent);
            stopSelf();
        });

        controlBoxView.findViewById(R.id.btnCropCancel).setOnClickListener(v -> stopSelf());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controlBoxView != null && windowManager != null) {
            try { windowManager.removeView(controlBoxView); } catch (Exception ignored) {}
        }
        if (cropFrameView != null && windowManager != null) {
            try { windowManager.removeView(cropFrameView); } catch (Exception ignored) {}
        }
    }
}
