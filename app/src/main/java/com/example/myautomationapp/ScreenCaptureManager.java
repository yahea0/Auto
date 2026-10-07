package com.example.myautomationapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;

public class ScreenCaptureManager {
    private static ScreenCaptureManager instance;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int screenWidth, screenHeight, screenDensity;

    public static synchronized ScreenCaptureManager getInstance() {
        if (instance == null) instance = new ScreenCaptureManager();
        return instance;
    }

    public void init(Context context, int resultCode, Intent data) {
        if (data == null || mediaProjection != null) return;

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        screenDensity = dm.densityDpi;

        MediaProjectionManager mpm = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            try {
                mediaProjection = mpm.getMediaProjection(resultCode, (Intent) data.clone());
                setupVirtualDisplay();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupVirtualDisplay() {
        if (mediaProjection == null) return;
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null
        );
    }

    /**
     * التقاط فريم حي ونظيف من الشاشة بالبكسل الحقيقي
     */
    public Bitmap captureScreen() {
        if (imageReader == null) return null;
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return null;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();

            if (rowPadding > 0) {
                Bitmap clean = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
                bitmap.recycle();
                return clean;
            }
            return bitmap;
        } catch (Exception e) {
            if (image != null) image.close();
            return null;
        }
    }
}
