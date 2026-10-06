package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;

public class VisionEngine {

    /**
     * خوارزمية مطابقة القوالب فائقة السرعة للأندرويد (Template Matching)
     * تبحث عن الصورة المستهدفة داخل شاشة الهاتف وتعيد إحداثيات مركزها (X, Y)
     */
    public static Point findImageOnScreen(Bitmap screen, Bitmap template, double similarityThreshold) {
        if (screen == null || template == null) return null;

        int screenWidth = screen.getWidth();
        int screenHeight = screen.getHeight();
        int templateWidth = template.getWidth();
        int templateHeight = template.getHeight();

        if (templateWidth > screenWidth || templateHeight > screenHeight) return null;

        int[] screenPixels = new int[screenWidth * screenHeight];
        screen.getPixels(screenPixels, 0, screenWidth, 0, 0, screenWidth, screenHeight);

        int[] templatePixels = new int[templateWidth * templateHeight];
        template.getPixels(templatePixels, 0, templateWidth, 0, 0, templateWidth, templateHeight);

        int bestX = -1;
        int bestY = -1;
        double maxScore = -1.0;

        // قفزات ذكية لتسريع البحث (Fast Scan Step)
        int step = 2;

        for (int y = 0; y <= screenHeight - templateHeight; y += step) {
            for (int x = 0; x <= screenWidth - templateWidth; x += step) {
                int matchedPixels = 0;
                int totalChecked = 0;

                for (int ty = 0; ty < templateHeight; ty += 2) {
                    for (int tx = 0; tx < templateWidth; tx += 2) {
                        int screenColor = screenPixels[(y + ty) * screenWidth + (x + tx)];
                        int templateColor = templatePixels[ty * templateWidth + tx];

                        // فحص تشابه لون البكسل
                        int diffR = Math.abs(((screenColor >> 16) & 0xFF) - ((templateColor >> 16) & 0xFF));
                        int diffG = Math.abs(((screenColor >> 8) & 0xFF) - ((templateColor >> 8) & 0xFF));
                        int diffB = Math.abs((screenColor & 0xFF) - (templateColor & 0xFF));

                        if (diffR + diffG + diffB < 60) {
                            matchedPixels++;
                        }
                        totalChecked++;
                    }
                }

                double score = (double) matchedPixels / totalChecked;
                if (score > maxScore) {
                    maxScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        // إذا كانت نسبة التطابق أكبر من الحد المطلوب (مثلاً 80%)
        if (maxScore >= similarityThreshold) {
            return new Point(bestX + (templateWidth / 2), bestY + (templateHeight / 2));
        }

        return null;
    }
}
