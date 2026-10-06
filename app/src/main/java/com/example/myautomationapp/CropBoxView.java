package com.example.myautomationapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class CropBoxView extends View {
    public RectF boxRect = new RectF(200, 400, 600, 800);
    private Paint borderPaint, handlePaint, transparentPaint;
    private int activeHandle = -1;
    private float lastX, lastY;

    public CropBoxView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#FFD700"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);

        handlePaint = new Paint();
        handlePaint.setColor(Color.parseColor("#FFD700"));
        handlePaint.setStyle(Paint.Style.FILL);

        transparentPaint = new Paint();
        transparentPaint.setColor(Color.TRANSPARENT);
        transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // تعتيم الشاشة المحيطة
        canvas.drawColor(Color.parseColor("#88000000"));
        // تفريغ المربع في المنتصف لرؤية الزر تحته
        canvas.drawRect(boxRect, transparentPaint);
        // رسم الإطار الذهبي
        canvas.drawRect(boxRect, borderPaint);

        // رسم مقابض الزوايا للتحكم بالحجم
        canvas.drawCircle(boxRect.left, boxRect.top, 25f, handlePaint);
        canvas.drawCircle(boxRect.right, boxRect.top, 25f, handlePaint);
        canvas.drawCircle(boxRect.left, boxRect.bottom, 25f, handlePaint);
        canvas.drawCircle(boxRect.right, boxRect.bottom, 25f, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                activeHandle = getTouchedHandle(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = x - lastX;
                float dy = y - lastY;

                if (activeHandle == 1) { // الزاوية العلوية اليسرى
                    boxRect.left = Math.min(boxRect.left + dx, boxRect.right - 100);
                    boxRect.top = Math.min(boxRect.top + dy, boxRect.bottom - 100);
                } else if (activeHandle == 2) { // الزاوية العلوية اليمنى
                    boxRect.right = Math.max(boxRect.right + dx, boxRect.left + 100);
                    boxRect.top = Math.min(boxRect.top + dy, boxRect.bottom - 100);
                } else if (activeHandle == 3) { // الزاوية السفلية اليسرى
                    boxRect.left = Math.min(boxRect.left + dx, boxRect.right - 100);
                    boxRect.bottom = Math.max(boxRect.bottom + dy, boxRect.top + 100);
                } else if (activeHandle == 4) { // الزاوية السفلية اليمنى
                    boxRect.right = Math.max(boxRect.right + dx, boxRect.left + 100);
                    boxRect.bottom = Math.max(boxRect.bottom + dy, boxRect.top + 100);
                } else if (boxRect.contains(x, y)) { // سحب وتحريك المربع بالكامل
                    boxRect.offset(dx, dy);
                }

                lastX = x;
                lastY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                activeHandle = -1;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private int getTouchedHandle(float x, float y) {
        if (Math.hypot(x - boxRect.left, y - boxRect.top) < 60) return 1;
        if (Math.hypot(x - boxRect.right, y - boxRect.top) < 60) return 2;
        if (Math.hypot(x - boxRect.left, y - boxRect.bottom) < 60) return 3;
        if (Math.hypot(x - boxRect.right, y - boxRect.bottom) < 60) return 4;
        return 0;
    }
}
