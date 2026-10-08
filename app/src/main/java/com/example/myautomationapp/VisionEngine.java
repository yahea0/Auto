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
     * التحقق الشامل من وجود العلامة المميزة (سواء كانت لقطة صغيرة أو صورة مشهد كاملة)
     */
    public static boolean isAnchorPresentOnScreen(Bitmap screen, Bitmap anchorBmp, int anchorCropX, int anchorCropY, int tolerance, double minThreshold) {
        if (screen == null || anchorBmp == null) return false;

        // 1. فحص في مكانها المباشر أولاً
        double directSim = compareSubRegionStrict(screen, anchorBmp, anchorCropX, anchorCropY, tolerance, false);
        if (directSim >= minThreshold) {
            return true;
        }

        // 2. إذا تحركت الشاشة نبحث عنها في كامل الشاشة
        Point found = scanAndFindTemplate(screen, anchorBmp, 0, 0, screen.getWidth(), screen.getHeight(), minThreshold, tolerance, false);
        return (found != null);
    }

    /**
     * محرك البحث الهرمي السريع (Pyramidal Fast Matching): يمسح الشاشة كاملة في أقل من 5ms
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

        // 1. فحص فوري للموضع المتوقع
        double directSim = compareSubRegionStrict(screen, template, boundX, boundY, colorTolerance, ignoreBadge);
        if (directSim >= minThreshold) {
            return new Point(boundX, boundY);
        }

        // 2. تصغير هرمي سريع 3x للبحث الكلي بدون استهلاك المعالج
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

        Point bestPeak = null;
        double maxPeakSim = 0.0;

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

                        if ((dr + dg + db) / 3.0 <= 40) matched++;
                        count++;
                    }
                }

                if (count > 0) {
                    double sSim = ((double) matched / count) * 100.0;
                    if (sSim > maxPeakSim) {
                        maxPeakSim = sSim;
                        bestPeak = new Point(x * scale, y * scale);
                    }
                }
            }
        }

        smallScreen.recycle();
        smallTemplate.recycle();

        // 3. مسح دقيق بنقاء 1 بكسل فقط حول نقطة الذروة
        if (bestPeak != null && maxPeakSim >= Math.max(20.0, minThreshold - 35.0)) {
            int fineStartX = Math.max(boundX, bestPeak.x - (scale * 2));
            int fineEndX = Math.min(limitX, bestPeak.x + (scale * 2));
            int fineStartY = Math.max(boundY, bestPeak.y - (scale * 2));
            int fineEndY = Math.min(limitY, bestPeak.y + (scale * 2));

            Point exactMatch = null;
            double exactMaxSim = 0.0;

            for (int fy = fineStartY; fy <= fineEndY; fy++) {
                for (int fx = fineStartX; fx <= fineEndX; fx++) {
                    double strictSim = compareSubRegionStrict(screen, template, fx, fy, colorTolerance, ignoreBadge);
                    if (strictSim > exactMaxSim) {
                        exactMaxSim = strictSim;
                        exactMatch = new Point(fx, fy);
                    }
                }
            }

            if (exactMaxSim >= minThreshold && exactMatch != null) {
                return exactMatch;
            }
        }

        return null;
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        return scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, minThreshold, 35, false);
    }
}
