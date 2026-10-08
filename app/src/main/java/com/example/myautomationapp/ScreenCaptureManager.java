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
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/**
 * محرك التقاط الشاشة المباشر فائق السرعة (Real-Time 120 FPS Stream Pipeline)
 * مصمم لتفادي الـ Garbage Collector عبر حجز الذاكرة مسبقاً (Zero Allocation).
 */
public class ScreenCaptureManager {
    private static ScreenCaptureManager instance;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private HandlerThread captureThread;
    private Handler captureHandler;

    private int screenWidth, screenHeight, screenDensity;

    // نظام التخزين المزدوج (Double-Buffering)
    private Bitmap frontBitmap;
    private Bitmap backBitmap;
    private final Object frameLock = new Object();
    private ByteBuffer cleanDirectBuffer;

    private volatile boolean isRunning = false;

    public static synchronized ScreenCaptureManager getInstance() {
        if (instance == null) {
            instance = new ScreenCaptureManager();
        }
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

        // حجز نسختين من الصور مسبقاً لمنع إنشائها داخل حلقة التكرار
        frontBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        backBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        cleanDirectBuffer = ByteBuffer.allocateDirect(screenWidth * screenHeight * 4);

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

        // تخصيص خيط معالجة بأعلى أولوية رسومية في أندرويد لضمان استقرار الـ 120Hz
        captureThread = new HandlerThread("Realme120FpsCaptureThread", Process.THREAD_PRIORITY_URGENT_DISPLAY);
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        // سعة طابور 2 لإلغاء تأخير الفريمات والحصول على الصورة اللحظية فوراً
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        isRunning = true;

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null && isRunning) {
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int pixelStride = plane.getPixelStride();
                    int rowStride = plane.getRowStride();
                    int rowPadding = rowStride - (pixelStride * screenWidth);

                    if (rowPadding == 0) {
                        backBitmap.copyPixelsFromBuffer(buffer);
                    } else {
                        cleanDirectBuffer.clear();
                        int rowBytes = screenWidth * 4;
                        int srcPos = 0;
                        for (int r = 0; r < screenHeight; r++) {
                            buffer.position(srcPos);
                            buffer.limit(srcPos + rowBytes);
                            cleanDirectBuffer.put(buffer);
                            srcPos += rowStride;
                        }
                        cleanDirectBuffer.rewind();
                        backBitmap.copyPixelsFromBuffer(cleanDirectBuffer);
                    }

                    // تبديل الفريمات بلحظة واحدة وبدون استهلاك للذاكرة
                    synchronized (frameLock) {
                        Bitmap temp = frontBitmap;
                        frontBitmap = backBitmap;
                        backBitmap = temp;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, captureHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "Live120FpsVirtualDisplay",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, captureHandler
        );
    }

    /**
     * إرجاع الفريم الحي المباشر دون عمل copy() لضمان استجابة صفرية التأخير.
     */
    public Bitmap captureScreen() {
        synchronized (frameLock) {
            if (frontBitmap != null && !frontBitmap.isRecycled()) {
                return frontBitmap;
            }
        }
        return null;
    }

    /**
     * لحفظ لقطة شاشة كملف مستقل دون التأثير على البث المباشر.
     */
    public Bitmap captureScreenSnapshot() {
        synchronized (frameLock) {
            if (frontBitmap != null && !frontBitmap.isRecycled()) {
                return frontBitmap.copy(Bitmap.Config.ARGB_8888, false);
            }
        }
        return null;
    }

    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    public void stop() {
        isRunning = false;
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (captureThread != null) { captureThread.quitSafely(); captureThread = null; }
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
    }
}
