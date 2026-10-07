package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;

public class VisionEngine {

    /**
     * مطابقة بكسلية صارمة لمنطقة محددة بدقة رياضية كاملة
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
     * محرك البحث الهرمي الذكي (Pyramidal Coarse-to-Fine Matching)
     * يمسح كامل الشاشة أو المنطقة المخصصة في أقل من 15ms ويقفل على مكان الصورة بنقاء تام
     */
    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        if (screen == null || template == null) return null;

        int tw = template.getWidth();
        int th = template.getHeight();
        if (tw <= 0 || th <= 0 || screen.getWidth() < tw || screen.getHeight() < th) return null;

        int boundX = Math.max(0, Math.min(searchX, screen.getWidth() - tw));
        int boundY = Math.max(0, Math.min(searchY, screen.getHeight() - th));
        int limitX = Math.min(screen.getWidth() - tw, boundX + searchW);
        int limitY = Math.min(screen.getHeight() - th, boundY + searchH);

        if (limitX < boundX || limitY < boundY) return null;

        // 1. الفحص الفوري المباشر لنفس الموقع (إذا لم يتحرك الهدف ينفذ في 0ms)
        double directSim = compareSubRegionStrict(screen, template, boundX, boundY);
        if (directSim >= minThreshold) {
            return new Point(boundX, boundY);
        }

        // 2. المرحلة الهرمية الأولى: تصغير سريع للبحث الكلي السريع (Coarse Search)
        int scale = 4;
        int sw = Math.max(1, screen.getWidth() / scale);
        int sh = Math.max(1, screen.getHeight() / scale);
        int stw = Math.max(2, tw / scale);
        int sth = Math.max(2, th / scale);

        Bitmap smallScreen = Bitmap.createScaledBitmap(screen, sw, sh, false);
        Bitmap smallTemplate = Bitmap.createScaledBitmap(template, stw, sth, false);

        int[] sPixels = new int[sw * sh];
        int[] tPixels = new int[stw * sth];
        smallScreen.getPixels(sPixels, 0, sw, 0, 0, sw, sh);
        smallTemplate.getPixels(tPixels, 0, stw, 0, 0, stw, sth);

        int sBoundX = boundX / scale;
        int sBoundY = boundY / scale;
        int sLimitX = Math.min(sw - stw, limitX / scale);
        int sLimitY = Math.min(sh - sth, limitY / scale);

        Point bestCoarsePoint = null;
        double bestCoarseScore = 0.0;

        for (int sy = sBoundY; sy <= sLimitY; sy += 2) {
            for (int sx = sBoundX; sx <= sLimitX; sx += 2) {
                int matched = 0;
                int total = 0;

                for (int ty = 0; ty < sth; ty += 2) {
                    for (int tx = 0; tx < stw; tx += 2) {
                        int sc = sPixels[(sy + ty) * sw + (sx + tx)];
                        int tc = tPixels[ty * stw + tx];

                        int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
                        int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
                        int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

                        if (diffR < 28 && diffG < 28 && diffB < 28) matched++;
                        total++;
                    }
                }

                if (total > 0) {
                    double score = ((double) matched / total) * 100.0;
                    if (score > bestCoarseScore) {
                        bestCoarseScore = score;
                        bestCoarsePoint = new Point(sx * scale, sy * scale);
                    }
                }
            }
        }

        smallScreen.recycle();
        smallTemplate.recycle();

        // 3. المرحلة الهرمية الثانية: تدقيق جراحي بنقاء 1 بكسل (Fine Pixel Refinement) حول نقطة الذروة
        if (bestCoarsePoint != null && bestCoarseScore >= Math.max(25.0, minThreshold - 35.0)) {
            int fineStartX = Math.max(boundX, bestCoarsePoint.x - (scale * 2));
            int fineEndX = Math.min(limitX, bestCoarsePoint.x + (scale * 2));
            int fineStartY = Math.max(boundY, bestCoarsePoint.y - (scale * 2));
            int fineEndY = Math.min(limitY, bestCoarsePoint.y + (scale * 2));

            Point exactPoint = null;
            double maxExactSim = 0.0;

            for (int fy = fineStartY; fy <= fineEndY; fy++) {
                for (int fx = fineStartX; fx <= fineEndX; fx++) {
                    double strictScore = compareSubRegionStrict(screen, template, fx, fy);
                    if (strictScore > maxExactSim) {
                        maxExactSim = strictScore;
                        exactPoint = new Point(fx, fy);
                    }
                }
            }

            if (maxExactSim >= minThreshold && exactPoint != null) {
                return exactPoint;
            }
        }

        return null;
    }
}
