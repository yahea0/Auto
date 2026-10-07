package com.example.myautomationapp;

import android.graphics.Bitmap;

public class VisionEngine {

    /**
     * مقارنة سريعة ودقيقة جداً للمنطقة المحددة (تستغرق أقل من 2 ميلي ثانية)
     * تعيد نسبة التطابق من 0 إلى 100%
     */
    public static double compareSubRegion(Bitmap screen, Bitmap template, int cropX, int cropY) {
        if (screen == null || template == null) return 0.0;

        int tw = template.getWidth();
        int th = template.getHeight();

        int safeX = Math.max(0, Math.min(cropX, screen.getWidth() - tw));
        int safeY = Math.max(0, Math.min(cropY, screen.getHeight() - th));

        int[] screenPixels = new int[tw * th];
        int[] templatePixels = new int[tw * th];

        screen.getPixels(screenPixels, 0, tw, safeX, safeY, tw, th);
        template.getPixels(templatePixels, 0, tw, 0, 0, tw, th);

        int matchedCount = 0;
        int totalSampled = 0;

        // عينات نقطية سريعة لحساب التطابق اللوني
        for (int i = 0; i < screenPixels.length; i += 2) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
            int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
            int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

            // تسامح لوني ذكي (Tolerance) لمراعاة ظلال الشاشة
            if (diffR + diffG + diffB < 65) {
                matchedCount++;
            }
            totalSampled++;
        }

        if (totalSampled == 0) return 0.0;
        return ((double) matchedCount / totalSampled) * 100.0;
    }
}
