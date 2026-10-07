package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;

public class VisionEngine {

    /**
     * مطابقة فائقة لمنطقة محددة (Captured Location أو Custom Region)
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

        // إذا كانت الصورة صغيرة جداً (أقل من 35 بكسل) نقوم بفحص بكسلي كامل دون تقسيم شبكي
        if (tw < 35 || th < 35) {
            int matched = 0;
            int total = screenPixels.length;
            for (int i = 0; i < total; i++) {
                int sc = screenPixels[i]; int tc = templatePixels[i];
                int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
                int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
                int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));
                if (diffR < 20 && diffG < 20 && diffB < 20) matched++;
            }
            return ((double) matched / total) * 100.0;
        }

        // للصور الكبيرة والمتوسطة: فحص المصفوفة الكتلية 16-Grid
        int gridCols = 4; int gridRows = 4;
        int cellW = Math.max(1, tw / gridCols);
        int cellH = Math.max(1, th / gridRows);

        double minBlockScore = 100.0;
        double totalWeightedMatch = 0.0;
        double totalWeight = 0.0;

        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                int blockMatched = 0;
                int blockTotal = 0;
                int startX = c * cellW; int startY = r * cellH;
                int endX = (c == gridCols - 1) ? tw : startX + cellW;
                int endY = (r == gridRows - 1) ? th : startY + cellH;

                for (int y = startY; y < endY; y += 2) {
                    for (int x = startX; x < endX; x += 2) {
                        int idx = y * tw + x;
                        int sc = screenPixels[idx]; int tc = templatePixels[idx];

                        int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
                        int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
                        int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

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
                    if (blockScore < minBlockScore) minBlockScore = blockScore;
                    totalWeightedMatch += blockMatched;
                    totalWeight += blockTotal;
                }
            }
        }

        if (totalWeight == 0) return 0.0;
        double overallScore = (totalWeightedMatch / totalWeight) * 100.0;
        return Math.max(0.0, Math.min(100.0, (overallScore * 0.65) + (minBlockScore * 0.35)));
    }

    /**
     * خوارزمية المسح الشامل (Full Screen أو Custom Region) للبحث عن الصورة في أي مكان بالنافذة
     */
    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        if (screen == null || template == null) return null;

        int tw = template.getWidth();
        int th = template.getHeight();

        int boundX = Math.max(0, Math.min(searchX, screen.getWidth() - tw));
        int boundY = Math.max(0, Math.min(searchY, screen.getHeight() - th));
        int limitX = Math.min(screen.getWidth() - tw, boundX + searchW);
        int limitY = Math.min(screen.getHeight() - th, boundY + searchH);

        Point bestPoint = null;
        double highestSim = 0.0;

        int step = (tw > 80 && th > 80) ? 6 : 3;

        for (int y = boundY; y <= limitY; y += step) {
            for (int x = boundX; x <= limitX; x += step) {
                double sim = compareSubRegionStrict(screen, template, x, y);
                if (sim > highestSim) {
                    highestSim = sim;
                    bestPoint = new Point(x, y);
                }
            }
        }

        if (highestSim >= minThreshold && bestPoint != null) {
            return bestPoint;
        }
        return null;
    }
}
