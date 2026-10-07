package com.example.myautomationapp;

import android.graphics.Bitmap;

public class VisionEngine {

    /**
     * خوارزمية الرؤية الفائقة لمطابقة الصور العملاقة بدقة متناهية (Matrix Quadrant + Feature Weighted)
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

        // 1. فحص تماسك الكتل (4x4 Grid = 16 كتلة جغرافية لمنع تمرير الصور الكبيرة إذا تغير جزء منها)
        int gridCols = 4;
        int gridRows = 4;
        int cellW = Math.max(1, tw / gridCols);
        int cellH = Math.max(1, th / gridRows);

        double minBlockScore = 100.0;
        double totalWeightedMatch = 0.0;
        double totalWeight = 0.0;

        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                int blockMatched = 0;
                int blockTotal = 0;

                int startX = c * cellW;
                int startY = r * cellH;
                int endX = (c == gridCols - 1) ? tw : startX + cellW;
                int endY = (r == gridRows - 1) ? th : startY + cellH;

                for (int y = startY; y < endY; y += 2) {
                    for (int x = startX; x < endX; x += 2) {
                        int idx = y * tw + x;
                        int sc = screenPixels[idx];
                        int tc = templatePixels[idx];

                        int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
                        int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
                        int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

                        // فحص تباين البكسل (إذا كان تفصيلاً مهماً وليس خلفية داكنة سادة يحصل على وزن 3x)
                        int lum = (diffR + diffG + diffB);
                        int weight = (lum > 30) ? 3 : 1;

                        if (diffR < 18 && diffG < 18 && diffB < 18) {
                            blockMatched += weight;
                        }
                        blockTotal += weight;
                    }
                }

                if (blockTotal > 0) {
                    double blockScore = ((double) blockMatched / blockTotal) * 100.0;
                    if (blockScore < minBlockScore) {
                        minBlockScore = blockScore;
                    }
                    totalWeightedMatch += blockMatched;
                    totalWeight += blockTotal;
                }
            }
        }

        if (totalWeight == 0) return 0.0;

        double overallScore = (totalWeightedMatch / totalWeight) * 100.0;

        // دمج النتيجة العامة مع أضعف كتلة (إذا تغير جزء بنسبة كبيرة يسقط التقييم فوراً)
        double finalStrictScore = (overallScore * 0.65) + (minBlockScore * 0.35);

        return Math.max(0.0, Math.min(100.0, finalStrictScore));
    }
}
