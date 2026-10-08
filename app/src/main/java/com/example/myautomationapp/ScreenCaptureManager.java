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

public class ScreenCaptureManager {
    private static ScreenCaptureManager instance;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private HandlerThread captureThread;
    private Handler captureHandler;

    private int screenWidth, screenHeight, screenDensity;

    private Bitmap masterBitmap;
    private final Object frameLock = new Object();
    private byte[] rowByteArray;
    private ByteBuffer cleanBuffer;

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

        masterBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        rowByteArray = new byte[screenWidth * 4];
        cleanBuffer = ByteBuffer.allocateDirect(screenWidth * screenHeight * 4);

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

        captureThread = new HandlerThread("ScreenCapture120Hz", Process.THREAD_PRIORITY_URGENT_DISPLAY);
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3);
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

                    synchronized (frameLock) {
                        if (rowPadding == 0) {
                            buffer.rewind();
                            masterBitmap.copyPixelsFromBuffer(buffer);
                        } else {
                            cleanBuffer.clear();
                            int rowBytes = screenWidth * 4;
                            for (int y = 0; y < screenHeight; y++) {
                                int offset = y * rowStride;
                                if (offset + rowBytes <= buffer.capacity()) {
                                    buffer.position(offset);
                                    buffer.get(rowByteArray, 0, rowBytes);
                                    cleanBuffer.put(rowByteArray, 0, rowBytes);
                                }
                            }
                            cleanBuffer.rewind();
                            masterBitmap.copyPixelsFromBuffer(cleanBuffer);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, captureHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "LiveCaptureDisplay",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, captureHandler
        );
    }

    /**
     * إرجاع لقطة شاشة حية غير قابلة للتلف حتى عند استدعاء recycle() من قِص الشريط المطاطي
     */
    public Bitmap captureScreen() {
        synchronized (frameLock) {
            if (masterBitmap != null && !masterBitmap.isRecycled()) {
                return masterBitmap.copy(Bitmap.Config.ARGB_8888, false);
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
