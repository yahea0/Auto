package com.example.myautomationapp;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
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
import android.widget.TextView;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;

public class ImageCropPickerService extends Service {
    private WindowManager windowManager;
    private View controlBoxView;
    private View cropFrameView;
    private WindowManager.LayoutParams boxParams;
    private WindowManager.LayoutParams frameParams;
    private TextView tvCropCoords, tvCropDimensions;
    private int targetClickX, targetClickY;
    private boolean isStandaloneClickImage = false; // هل القص لأكشن Click Image مستقل؟

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            targetClickX = intent.getIntExtra("target_x", 500);
            targetClickY = intent.getIntExtra("target_y", 1000);
            isStandaloneClickImage = intent.getBooleanExtra("is_click_image", false);
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

        controlBoxView = LayoutInflater.from(themedContext).inflate(R.layout.crop_picker_box_layout, null);
        tvCropCoords = controlBoxView.findViewById(R.id.tvCropCoords);
        tvCropDimensions = controlBoxView.findViewById(R.id.tvCropDimensions);

        boxParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, flags, PixelFormat.TRANSLUCENT);
        boxParams.gravity = Gravity.TOP | Gravity.START;
        boxParams.x = (dm.widthPixels / 2) - (int) (110 * dm.density);
        boxParams.y = (int) (60 * dm.density);

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
                        initX = frameParams.x; initY = frameParams.y;
                        initW = frameParams.width; initH = frameParams.height;
                        touchX = event.getRawX(); touchY = event.getRawY();
                        isResizing = (event.getX() > frameParams.width - 90 && event.getY() > frameParams.height - 90);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchX;
                        float dy = event.getRawY() - touchY;

                        if (isResizing) {
                            int newW = Math.max((int) (50 * getResources().getDisplayMetrics().density), initW + (int) dx);
                            int newH = Math.max((int) (35 * getResources().getDisplayMetrics().density), initH + (int) dy);
                            frameParams.width = newW;
                            frameParams.height = newH;
                        } else {
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

        controlBoxView.findViewById(R.id.btnCropConfirm).setOnClickListener(v -> captureAndSaveTemplate());
        controlBoxView.findViewById(R.id.btnCropCancel).setOnClickListener(v -> stopSelf());
    }

    private void captureAndSaveTemplate() {
        controlBoxView.setVisibility(View.GONE);
        cropFrameView.setVisibility(View.GONE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String savedPath = null;
            try {
                Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                if (screen != null) {
                    int cx = Math.max(0, Math.min(frameParams.x, screen.getWidth() - 10));
                    int cy = Math.max(0, Math.min(frameParams.y, screen.getHeight() - 10));
                    int cw = Math.min(frameParams.width, screen.getWidth() - cx);
                    int ch = Math.min(frameParams.height, screen.getHeight() - cy);

                    if (cw > 10 && ch > 10) {
                        Bitmap cropped = Bitmap.createBitmap(screen, cx, cy, cw, ch);
                        File file = new File(getFilesDir(), "img_" + System.currentTimeMillis() + ".png");
                        FileOutputStream fos = new FileOutputStream(file);
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                        savedPath = file.getAbsolutePath();
                    }
                    screen.recycle();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Intent intent = new Intent(isStandaloneClickImage ? "CLICK_IMAGE_ACTION_CROPPED" : "IMAGE_TEMPLATE_CROPPED");
            intent.setPackage(getPackageName());
            intent.putExtra("crop_x", frameParams.x);
            intent.putExtra("crop_y", frameParams.y);
            intent.putExtra("crop_w", frameParams.width);
            intent.putExtra("crop_h", frameParams.height);
            intent.putExtra("target_x", targetClickX);
            intent.putExtra("target_y", targetClickY);
            intent.putExtra("image_path", savedPath);
            sendBroadcast(intent);
            stopSelf();
        }, 80);
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
