package com.example.myautomationapp;

import android.graphics.Bitmap;

public class VisionEngine {

    /**
     * مقارنة بكسلية رياضية صارمة ومطلقة (تدعم من 0% حتى 100% بالضبط)
     */
    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        if (screen == null || template == null) return 0.0;

        int tw = template.getWidth();
        int th = template.getHeight();

        int safeX = Math.max(0, Math.min(cropX, screen.getWidth() - tw));
        int safeY = Math.max(0, Math.min(cropY, screen.getHeight() - th));

        int[] screenPixels = new int[tw * th];
        int[] templatePixels = new int[tw * th];

        screen.getPixels(screenPixels, 0, tw, safeX, safeY, tw, th);
        template.getPixels(templatePixels, 0, tw, 0, 0, tw, th);

        double totalDifference = 0.0;
        int totalPixels = screenPixels.length;

        for (int i = 0; i < totalPixels; i++) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
            int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
            int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

            // حساب نسبة الخطأ في كل بكسل من 0.0 إلى 1.0
            totalDifference += (diffR + diffG + diffB) / (3.0 * 255.0);
        }

        double averageDifference = totalDifference / totalPixels;
        double similarityPercentage = (1.0 - averageDifference) * 100.0;

        return Math.max(0.0, Math.min(100.0, similarityPercentage));
    }
}
