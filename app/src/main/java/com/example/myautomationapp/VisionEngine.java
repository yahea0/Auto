package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;
import java.util.ArrayList;
import java.util.List;

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

    private static class SearchCandidate {
        int x, y;
        double score;
        SearchCandidate(int x, int y, double score) {
            this.x = x; this.y = y; this.score = score;
        }
    }

    /**
     * محرك البحث الهرمي الذكي فائق الدقة والسرعة (Two-Stage Precision Search)
     * يفحص الموضع المتوقع الحقيقي أولاً في 0.2ms، ثم يمسح الشاشة هرمياً دون أي خطأ مكاني
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

        // المرحلة 1: الفحص الفوري للموضع الأصلي المتوقع ومحيطه الصغير (±30 بكسل لتحمل حركة الخريطة الطفيفة في 1ms)
        if (expectedX >= 0 && expectedY >= 0) {
            int expSafeX = Math.max(boundX, Math.min(limitX, expectedX));
            int expSafeY = Math.max(boundY, Math.min(limitY, expectedY));

            // فحص دقيق في نفس المكان تماماً
            double directSim = compareSubRegionStrict(screen, template, expSafeX, expSafeY, colorTolerance, ignoreBadge);
            if (directSim >= minThreshold) {
                return new Point(expSafeX, expSafeY);
            }

            // فحص نافذة اهتزاز الكاميرا حول الموقع المتوقع
            Point localBest = null;
            double localMaxSim = 0.0;
            for (int dy = -30; dy <= 30; dy += 3) {
                for (int dx = -30; dx <= 30; dx += 3) {
                    int tx = Math.max(boundX, Math.min(limitX, expSafeX + dx));
                    int ty = Math.max(boundY, Math.min(limitY, expSafeY + dy));
                    double s = compareSubRegionStrict(screen, template, tx, ty, colorTolerance, ignoreBadge);
                    if (s > localMaxSim) {
                        localMaxSim = s;
                        localBest = new Point(tx, ty);
                    }
                }
            }
            if (localMaxSim >= minThreshold && localBest != null) {
                return localBest;
            }
        }

        // المرحلة 2: مسح المنطقة المحددة بالكامل أو الشاشة كاملة عبر التصغير الهرمي الذكي 3x
        int scale = 3;
        int scaledSw = sw / scale;
        int scaledSh = sh / scale;
        int scaledTw = Math.max(2, tw / scale);
        int scaledTh = Math.max(2, th / scale);

        Bitmap smallScreen = Bitmap.createScaledBitmap(screen, scaledSw, scaledSh, false);
        Bitmap smallTemplate = Bitmap.createScaledBitmap(template, scaledTw, scaledTh, false);

        int[] sPix = new int[scaledSw * scaledSh];
        int[] tPix = new int[scaledTw * scaledTh];
        smallScreen.getPixels(sPix, 0, scaledSw, 0, 0, scaledSw, scaledSh);
        smallTemplate.getPixels(tPix, 0, scaledTw, 0, 0, scaledTw, scaledTh);

        int sBoundX = boundX / scale;
        int sBoundY = boundY / scale;
        int sLimitX = Math.min(scaledSw - scaledTw, limitX / scale);
        int sLimitY = Math.min(scaledSh - scaledTh, limitY / scale);

        // جمع أفضل 5 مرشحات حقيقية لمنع الانخداع بأي رمال أو تفاصيل ثانوية
        List<SearchCandidate> topCandidates = new ArrayList<>();

        for (int y = sBoundY; y <= sLimitY; y += 2) {
            for (int x = sBoundX; x <= sLimitX; x += 2) {
                int matched = 0;
                int count = 0;

                for (int ty = 0; ty < scaledTh; ty += 2) {
                    for (int tx = 0; tx < scaledTw; tx += 2) {
                        int sc = sPix[(y + ty) * scaledSw + (x + tx)];
                        int tc = tPix[ty * scaledTw + tx];

                        int dr = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
                        int dg = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
                        int db = Math.abs((sc & 0xFF) - (tc & 0xFF));

                        if ((dr + dg + db) / 3.0 <= 42) matched++;
                        count++;
                    }
                }

                if (count > 0) {
                    double sSim = ((double) matched / count) * 100.0;
                    if (sSim >= Math.max(20.0, minThreshold - 35.0)) {
                        topCandidates.add(new SearchCandidate(x * scale, y * scale, sSim));
                        if (topCandidates.size() > 8) {
                            topCandidates.sort((a, b) -> Double.compare(b.score, a.score));
                            topCandidates.remove(topCandidates.size() - 1);
                        }
                    }
                }
            }
        }

        smallScreen.recycle();
        smallTemplate.recycle();

        // المرحلة 3: تدقيق جراحي بنقاء 1 بكسل فقط حول أفضل المرشحات لاقتناص الهدف الأصلي بالضبط
        Point finalExactPoint = null;
        double highestStrictSim = 0.0;

        for (SearchCandidate cand : topCandidates) {
            int fineStartX = Math.max(boundX, cand.x - (scale * 2));
            int fineEndX = Math.min(limitX, cand.x + (scale * 2));
            int fineStartY = Math.max(boundY, cand.y - (scale * 2));
            int fineEndY = Math.min(limitY, cand.y + (scale * 2));

            for (int fy = fineStartY; fy <= fineEndY; fy++) {
                for (int fx = fineStartX; fx <= fineEndX; fx++) {
                    double strictSim = compareSubRegionStrict(screen, template, fx, fy, colorTolerance, ignoreBadge);
                    if (strictSim > highestStrictSim) {
                        highestStrictSim = strictSim;
                        finalExactPoint = new Point(fx, fy);
                    }
                }
            }
        }

        if (highestStrictSim >= minThreshold && finalExactPoint != null) {
            return finalExactPoint;
        }

        return null;
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        return scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, searchX, searchY, minThreshold, 35, false);
    }
}
