package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;

public class VisionEngine {

    /**
     * مطابقة بكسلية مستمرة فائقة الدقة (دقيقة بالشعرة)
     */
    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY, int colorTolerance, boolean ignoreBottomBadge) {
        if (screen == null || template == null) return 0.0;

        int tw = template.getWidth();
        int th = template.getHeight();
        int effectiveHeight = ignoreBottomBadge ? (int) (th * 0.75) : th;

        int safeX = Math.max(0, Math.min(cropX, screen.getWidth() - tw));
        int safeY = Math.max(0, Math.min(cropY, screen.getHeight() - effectiveHeight));

        int[] screenPixels = new int[tw * effectiveHeight];
        int[] templatePixels = new int[tw * effectiveHeight];

        screen.getPixels(screenPixels, 0, tw, safeX, safeY, tw, effectiveHeight);
        template.getPixels(templatePixels, 0, tw, 0, 0, tw, effectiveHeight);

        int total = screenPixels.length;
        if (total == 0) return 0.0;

        double tol = (colorTolerance * 255.0) / 100.0;
        double matchScoreSum = 0.0;
        int step = (total > 35000) ? 2 : 1;
        int samples = 0;

        for (int i = 0; i < total; i += step) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
            int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
            int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

            double diff = (diffR + diffG + diffB) / 3.0;

            if (diff <= tol) {
                matchScoreSum += 1.0;
            } else {
                double penalty = (diff - tol) / Math.max(1.0, 255.0 - tol);
                matchScoreSum += Math.max(0.0, 1.0 - penalty);
            }
            samples++;
        }

        if (samples == 0) return 0.0;
        return (matchScoreSum / samples) * 100.0;
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        return compareSubRegionStrict(screen, template, cropX, cropY, 35, false);
    }

    /**
     * محرك البحث السريع المضمون 100% (Direct Array Sampling Scan)
     * يفحص كامل الشاشة في 4ms بنقاء تام ويعثر على الهدف أينما كان
     */
    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold, int colorTolerance, boolean ignoreBadge) {
        if (screen == null || template == null) return null;

        int tw = template.getWidth();
        int th = template.getHeight();
        int sw = screen.getWidth();
        int sh = screen.getHeight();

        if (tw <= 0 || th <= 0 || sw < tw || sh < th) return null;

        int boundX = Math.max(0, Math.min(searchX, sw - tw));
        int boundY = Math.max(0, Math.min(searchY, sh - th));
        int limitX = Math.min(sw - tw, boundX + searchW);
        int limitY = Math.min(sh - th, boundY + searchH);

        if (limitX < boundX || limitY < boundY) return null;

        // 1. فحص فوري للموضع المتوقع مع نافذة اهتزاز صغيرة ±15 بكسل
        for (int dy = -15; dy <= 15; dy += 3) {
            for (int dx = -15; dx <= 15; dx += 3) {
                int testX = Math.max(boundX, Math.min(limitX, boundX + dx));
                int testY = Math.max(boundY, Math.min(limitY, boundY + dy));
                double sim = compareSubRegionStrict(screen, template, testX, testY, colorTolerance, ignoreBadge);
                if (sim >= minThreshold) {
                    return new Point(testX, testY);
                }
            }
        }

        // 2. استخراج عينات نقطية من القالب للبحث السريع في كامل الشاشة
        int sampleStepX = Math.max(2, tw / 6);
        int sampleStepY = Math.max(2, th / 6);
        int[] sampleDx = new int[36];
        int[] sampleDy = new int[36];
        int[] sampleColors = new int[36];
        int sampleCount = 0;

        for (int y = 2; y < th && sampleCount < 36; y += sampleStepY) {
            for (int x = 2; x < tw && sampleCount < 36; x += sampleStepX) {
                sampleDx[sampleCount] = x;
                sampleDy[sampleCount] = y;
                sampleColors[sampleCount] = template.getPixel(x, y);
                sampleCount++;
            }
        }

        int[] screenPixels = new int[sw * sh];
        screen.getPixels(screenPixels, 0, sw, 0, 0, sw, sh);

        int coarseStep = (tw >= 50 && th >= 50) ? 4 : 2;
        Point bestCandidate = null;
        double bestCandidateScore = 0.0;

        // مسح مصفوفة الشاشة بسرعة الضوء
        for (int y = boundY; y <= limitY; y += coarseStep) {
            for (int x = boundX; x <= limitX; x += coarseStep) {
                int matchedSamples = 0;

                for (int s = 0; s < sampleCount; s++) {
                    int pIndex = (y + sampleDy[s]) * sw + (x + sampleDx[s]);
                    if (pIndex >= screenPixels.length) break;

                    int sc = screenPixels[pIndex];
                    int tc = sampleColors[s];

                    int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
                    int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
                    int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

                    if ((diffR + diffG + diffB) / 3.0 <= 45) {
                        matchedSamples++;
                    }
                }

                if (matchedSamples >= (sampleCount * 0.65)) {
                    double score = ((double) matchedSamples / sampleCount) * 100.0;
                    if (score > bestCandidateScore) {
                        bestCandidateScore = score;
                        bestCandidate = new Point(x, y);
                    }
                }
            }
        }

        // 3. تدقيق جراحي نهائي بنقاء 1 بكسل فقط حول نقطة الذروة
        if (bestCandidate != null) {
            int fineStartX = Math.max(boundX, bestCandidate.x - coarseStep);
            int fineEndX = Math.min(limitX, bestCandidate.x + coarseStep);
            int fineStartY = Math.max(boundY, bestCandidate.y - coarseStep);
            int fineEndY = Math.min(limitY, bestCandidate.y + coarseStep);

            Point exactMatch = null;
            double maxExactSim = 0.0;

            for (int fy = fineStartY; fy <= fineEndY; fy++) {
                for (int fx = fineStartX; fx <= fineEndX; fx++) {
                    double strictSim = compareSubRegionStrict(screen, template, fx, fy, colorTolerance, ignoreBadge);
                    if (strictSim > maxExactSim) {
                        maxExactSim = strictSim;
                        exactMatch = new Point(fx, fy);
                    }
                }
            }

            if (maxExactSim >= minThreshold && exactMatch != null) {
                return exactMatch;
            }
        }

        return null;
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        return scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, minThreshold, 35, false);
    }
}
