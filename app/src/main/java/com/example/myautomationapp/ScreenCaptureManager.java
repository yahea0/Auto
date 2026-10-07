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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;

public class ScreenCaptureManager {
    private static ScreenCaptureManager instance;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private volatile Bitmap latestScreenBitmap;
    private int screenWidth, screenHeight, screenDensity;

    public static synchronized ScreenCaptureManager getInstance() {
        if (instance == null) instance = new ScreenCaptureManager();
        return instance;
    }

    public void init(Context context, int resultCode, Intent data) {
        if (data == null || mediaProjection != null) return;

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics realDm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(realDm);
        screenWidth = realDm.widthPixels;
        screenHeight = realDm.heightPixels;
        screenDensity = realDm.densityDpi;

        MediaProjectionManager mpm = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            try {
                mediaProjection = mpm.getMediaProjection(resultCode, (Intent) data.clone());
                setupLiveCapture();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupLiveCapture() {
        if (mediaProjection == null) return;

        captureThread = new HandlerThread("LiveCaptureThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * screenWidth;

                    Bitmap bmp = Bitmap.createBitmap(
                            screenWidth + rowPadding / pixelStride,
                            screenHeight,
                            Bitmap.Config.ARGB_8888
                    );
                    bmp.copyPixelsFromBuffer(buffer);

                    if (rowPadding > 0) {
                        Bitmap clean = Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight);
                        bmp.recycle();
                        bmp = clean;
                    }

                    synchronized (ScreenCaptureManager.this) {
                        if (latestScreenBitmap != null && !latestScreenBitmap.isRecycled()) {
                            latestScreenBitmap.recycle();
                        }
                        latestScreenBitmap = bmp;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (image != null) image.close();
            }
        }, captureHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "LiveScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null
        );
    }

    /**
     * إرجاع الفريم الحي والمطابق 100% لما تراه عيناك على الشاشة الآن
     */
    public synchronized Bitmap captureScreen() {
        if (latestScreenBitmap != null && !latestScreenBitmap.isRecycled()) {
            return latestScreenBitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        return null;
    }
}
