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
     * عينات سريعة لاختبار المرشح في المسح الأولي
     */
    private static double fastSampleSimilarity(Bitmap screen, Bitmap template, int sx, int sy) {
        int tw = template.getWidth();
        int th = template.getHeight();
        int samples = 0;
        int matched = 0;

        for (int y = 0; y < th; y += 6) {
            for (int x = 0; x < tw; x += 6) {
                int sc = screen.getPixel(sx + x, sy + y);
                int tc = template.getPixel(x, y);

                int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
                int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
                int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

                if (diffR < 22 && diffG < 22 && diffB < 22) {
                    matched++;
                }
                samples++;
            }
        }
        return samples == 0 ? 0 : ((double) matched / samples) * 100.0;
    }

    /**
     * المسح الهرمي ثنائي الدقة (Pyramidal Two-Stage Scan)
     * يبحث في كامل الشاشة أو المنطقة المحددة ويعثر على الهدف بنقاء 1 بكسل
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

        // 1. فحص فوري للموضع الأصلي المباشر (توفير فائق للأداء إذا لم يتحرك العنصر)
        double directSim = compareSubRegionStrict(screen, template, boundX, boundY);
        if (directSim >= minThreshold) {
            return new Point(boundX, boundY);
        }

        // 2. مسح سريع لتحديد موقع الذروة التقريبي
        Point bestCandidate = null;
        double bestScore = 0.0;
        int coarseStep = (tw >= 50 && th >= 50) ? 5 : 3;

        for (int y = boundY; y <= limitY; y += coarseStep) {
            for (int x = boundX; x <= limitX; x += coarseStep) {
                double score = fastSampleSimilarity(screen, template, x, y);
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = new Point(x, y);
                }
            }
        }

        // 3. مسح تدقيق جراحي بخطوة 1 بكسل فقط حول نقطة الذروة للقفل على الهدف 100%
        if (bestCandidate != null && bestScore >= Math.max(30.0, minThreshold - 25.0)) {
            int fineStartX = Math.max(boundX, bestCandidate.x - coarseStep);
            int fineEndX = Math.min(limitX, bestCandidate.x + coarseStep);
            int fineStartY = Math.max(boundY, bestCandidate.y - coarseStep);
            int fineEndY = Math.min(limitY, bestCandidate.y + coarseStep);

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
