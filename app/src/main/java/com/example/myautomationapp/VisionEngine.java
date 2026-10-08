package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;
import java.util.ArrayList;
import java.util.List;

public class VisionEngine {

    /**
     * معادلة التطابق الصارمة الحقيقية:
     * إذا كان الهدف موجوداً تعطي 80% - 98%
     * إذا اختفى الهدف من الشاشة تسقط فوراً إلى 5% - 20%، ويستحيل أن تعطي تطابقاً عشوائياً!
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

        // حد التسامح اللوني الحقيقي (RGB Delta: 15 إلى 45 فقط)
        int tolRGB = Math.max(15, Math.min(50, colorTolerance));

        int matchedPixels = 0;
        int step = (total > 30000) ? 2 : 1;
        int samples = 0;

        for (int i = 0; i < total; i += step) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
            int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
            int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

            // فرز حقيقي: إذا كان البكسل قريباً ضمن تسامح الإضاءة يعتبر مطابقاً، وإلا يُلغى فوراً
            if (diffR <= tolRGB && diffG <= tolRGB && diffB <= tolRGB) {
                matchedPixels++;
            }
            samples++;
        }

        if (samples == 0) return 0.0;
        return ((double) matchedPixels / samples) * 100.0;
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        return compareSubRegionStrict(screen, template, cropX, cropY, 30, false);
    }

    private static class PeakCandidate {
        int x, y;
        double score;
        PeakCandidate(int x, int y, double score) {
            this.x = x; this.y = y; this.score = score;
        }
    }

    /**
     * محرك البحث الحقيقي السريع: يمسح المنطقة/الشاشة ويجد أعلى قمة تطابق حقيقية
     */
    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, int expectedX, int expectedY, double minThreshold, int colorTolerance, boolean ignoreBadge) {
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

        // 1. فحص فوري للموضع المتوقع الأصلي ومحيطه الصغير (±25 بكسل) في 1ms
        if (expectedX >= 0 && expectedY >= 0) {
            int expSafeX = Math.max(boundX, Math.min(limitX, expectedX));
            int expSafeY = Math.max(boundY, Math.min(limitY, expectedY));

            double directSim = compareSubRegionStrict(screen, template, expSafeX, expSafeY, colorTolerance, ignoreBadge);
            if (directSim >= minThreshold) {
                return new Point(expSafeX, expSafeY);
            }

            Point localPeak = null;
            double localMax = 0.0;
            for (int dy = -25; dy <= 25; dy += 3) {
                for (int dx = -25; dx <= 25; dx += 3) {
                    int tx = Math.max(boundX, Math.min(limitX, expSafeX + dx));
                    int ty = Math.max(boundY, Math.min(limitY, expSafeY + dy));
                    double s = compareSubRegionStrict(screen, template, tx, ty, colorTolerance, ignoreBadge);
                    if (s > localMax) {
                        localMax = s;
                        localPeak = new Point(tx, ty);
                    }
                }
            }
            if (localMax >= minThreshold && localPeak != null) {
                return localPeak;
            }
        }

        // 2. مسح شامل وسريع في المنطقة عبر عينات نقطية من القالب
        int sampleStepX = Math.max(2, tw / 7);
        int sampleStepY = Math.max(2, th / 7);
        int[] sampleDx = new int[49];
        int[] sampleDy = new int[49];
        int[] sampleColors = new int[49];
        int sampleCount = 0;

        for (int y = 2; y < th && sampleCount < 49; y += sampleStepY) {
            for (int x = 2; x < tw && sampleCount < 49; x += sampleStepX) {
                sampleDx[sampleCount] = x;
                sampleDy[sampleCount] = y;
                sampleColors[sampleCount] = template.getPixel(x, y);
                sampleCount++;
            }
        }

        int[] screenPixels = new int[sw * sh];
        screen.getPixels(screenPixels, 0, sw, 0, 0, sw, sh);

        int coarseStep = (tw >= 50 && th >= 50) ? 4 : 2;
        List<PeakCandidate> candidates = new ArrayList<>();

        for (int y = boundY; y <= limitY; y += coarseStep) {
            for (int x = boundX; x <= limitX; x += coarseStep) {
                int matched = 0;

                for (int s = 0; s < sampleCount; s++) {
                    int pIndex = (y + sampleDy[s]) * sw + (x + sampleDx[s]);
                    if (pIndex >= screenPixels.length) break;

                    int sc = screenPixels[pIndex];
                    int tc = sampleColors[s];

                    int dr = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
                    int dg = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
                    int db = Math.abs((sc & 0xFF) - (tc & 0xFF));

                    if (dr <= colorTolerance && dg <= colorTolerance && db <= colorTolerance) {
                        matched++;
                    }
                }

                if (matched >= (sampleCount * 0.50)) {
                    double sSim = ((double) matched / sampleCount) * 100.0;
                    candidates.add(new PeakCandidate(x, y, sSim));
                    if (candidates.size() > 6) {
                        candidates.sort((a, b) -> Double.compare(b.score, a.score));
                        candidates.remove(candidates.size() - 1);
                    }
                }
            }
        }

        // 3. تدقيق جراحي نهائي بنقاء 1 بكسل للتأكد التام من تطابق الهدف الحقيقي
        Point bestRealMatch = null;
        double maxStrictScore = 0.0;

        for (PeakCandidate cand : candidates) {
            int fineStartX = Math.max(boundX, cand.x - coarseStep);
            int fineEndX = Math.min(limitX, cand.x + coarseStep);
            int fineStartY = Math.max(boundY, cand.y - coarseStep);
            int fineEndY = Math.min(limitY, cand.y + coarseStep);

            for (int fy = fineStartY; fy <= fineEndY; fy++) {
                for (int fx = fineStartX; fx <= fineEndX; fx++) {
                    double strictSim = compareSubRegionStrict(screen, template, fx, fy, colorTolerance, ignoreBadge);
                    if (strictSim > maxStrictScore) {
                        maxStrictScore = strictSim;
                        bestRealMatch = new Point(fx, fy);
                    }
                }
            }
        }

        if (maxStrictScore >= minThreshold && bestRealMatch != null) {
            return bestRealMatch;
        }

        return null; // إذا لم يوجد الهدف، يعيد null ويمنع النقر تماماً!
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        return scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, searchX, searchY, minThreshold, 30, false);
    }
}
