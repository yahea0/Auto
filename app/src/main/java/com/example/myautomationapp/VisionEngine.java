package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;

public class VisionEngine {

    public static Point findActionTarget(Bitmap screen, Bitmap template, Action action) {
        if (screen == null || template == null || action == null) return null;

        String mode = action.getDetectLocationMode();
        if (mode == null) mode = "CAPTURED";

        int sw = screen.getWidth();
        int sh = screen.getHeight();

        int searchX = 0, searchY = 0, searchW = sw, searchH = sh;
        int expX = -1, expY = -1;

        if ("CAPTURED".equalsIgnoreCase(mode)) {
            expX = action.getCropX();
            expY = action.getCropY();
            searchX = Math.max(0, expX - 35);
            searchY = Math.max(0, expY - 35);
            searchW = action.getCropW() + 70;
            searchH = action.getCropH() + 70;
        } else if ("CUSTOM".equalsIgnoreCase(mode)) {
            searchX = action.getCustomRegionX();
            searchY = action.getCustomRegionY();
            searchW = action.getCustomRegionW();
            searchH = action.getCustomRegionH();
        } else {
            searchX = 0;
            searchY = 0;
            searchW = sw;
            searchH = sh;
        }

        double threshold = action.getSimilarity() > 0 ? action.getSimilarity() : 70.0;
        return scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, expX, expY, threshold, 30, false);
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template,
                                           int searchX, int searchY, int searchW, int searchH,
                                           int expectedX, int expectedY,
                                           double minThreshold, int colorTolerance, boolean ignoreBadge) {
        if (screen == null || template == null || screen.isRecycled() || template.isRecycled()) return null;

        int sw = screen.getWidth();
        int sh = screen.getHeight();
        int tw = template.getWidth();
        int th = template.getHeight();

        int effectiveTh = ignoreBadge ? (int) (th * 0.75) : th;
        if (tw <= 4 || effectiveTh <= 4 || sw < tw || sh < effectiveTh) return null;

        int boundX = Math.max(0, searchX);
        int boundY = Math.max(0, searchY);
        int limitW = Math.min(sw - boundX, searchW);
        int limitH = Math.min(sh - boundY, searchH);

        if (limitW < tw || limitH < effectiveTh) return null;

        // 1. المسار الفوري لوضع Captured Location
        if (expectedX >= 0 && expectedY >= 0) {
            int testX = Math.max(boundX, Math.min(sw - tw, expectedX));
            int testY = Math.max(boundY, Math.min(sh - effectiveTh, expectedY));

            double directSim = compareSubRegionStrict(screen, template, testX, testY, colorTolerance, ignoreBadge);
            if (directSim >= minThreshold) {
                return new Point(testX, testY);
            }

            Point localBest = null;
            double localMax = 0.0;
            for (int dy = -20; dy <= 20; dy += 4) {
                for (int dx = -20; dx <= 20; dx += 4) {
                    int curX = testX + dx;
                    int curY = testY + dy;
                    if (curX >= boundX && curX + tw <= sw && curY >= boundY && curY + effectiveTh <= sh) {
                        double s = compareSubRegionStrict(screen, template, curX, curY, colorTolerance, ignoreBadge);
                        if (s > localMax) {
                            localMax = s;
                            localBest = new Point(curX, curY);
                        }
                    }
                }
            }
            if (localMax >= minThreshold && localBest != null) {
                return localBest;
            }
        }

        // 2. الفحص الهرمي السريع (Pyramid Multi-Scale)
        int scale = (limitW > 400 && limitH > 400) ? 4 : 2;

        int pyrSw = limitW / scale;
        int pyrSh = limitH / scale;
        int pyrTw = Math.max(2, tw / scale);
        int pyrTh = Math.max(2, effectiveTh / scale);

        if (pyrSw < pyrTw || pyrSh < pyrTh) return null;

        float[] tplLuma = new float[pyrTw * pyrTh];
        float tplSum = 0;
        int[] tplColors = new int[tw * effectiveTh];
        template.getPixels(tplColors, 0, tw, 0, 0, tw, effectiveTh);

        for (int y = 0; y < pyrTh; y++) {
            for (int x = 0; x < pyrTw; x++) {
                int origX = x * scale;
                int origY = y * scale;
                int c = tplColors[origY * tw + origX];
                float lum = (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 151 + (c & 0xFF) * 28) >> 8;
                tplLuma[y * pyrTw + x] = lum;
                tplSum += lum;
            }
        }

        float tplMean = tplSum / (pyrTw * pyrTh);
        float tplVar = 0;
        for (int i = 0; i < tplLuma.length; i++) {
            tplLuma[i] -= tplMean;
            tplVar += tplLuma[i] * tplLuma[i];
        }

        if (tplVar < 10.0f) tplVar = 10.0f;

        int[] screenRoi = new int[limitW * limitH];
        screen.getPixels(screenRoi, 0, limitW, boundX, boundY, limitW, limitH);

        float[] screenLuma = new float[pyrSw * pyrSh];
        for (int y = 0; y < pyrSh; y++) {
            for (int x = 0; x < pyrSw; x++) {
                int c = screenRoi[(y * scale) * limitW + (x * scale)];
                screenLuma[y * pyrSw + x] = (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 151 + (c & 0xFF) * 28) >> 8;
            }
        }

        int coarseStep = 2;
        float bestCorr = -1.0f;
        int candX = -1, candY = -1;

        for (int y = 0; y <= pyrSh - pyrTh; y += coarseStep) {
            for (int x = 0; x <= pyrSw - pyrTw; x += coarseStep) {
                float patchSum = 0;
                for (int ty = 0; ty < pyrTh; ty++) {
                    int pIdx = (y + ty) * pyrSw + x;
                    for (int tx = 0; tx < pyrTw; tx++) {
                        patchSum += screenLuma[pIdx + tx];
                    }
                }
                float patchMean = patchSum / (pyrTw * pyrTh);

                float patchVar = 0;
                float cross = 0;
                for (int ty = 0; ty < pyrTh; ty++) {
                    int pIdx = (y + ty) * pyrSw + x;
                    for (int tx = 0; tx < pyrTw; tx++) {
                        float pDiff = screenLuma[pIdx + tx] - patchMean;
                        patchVar += pDiff * pDiff;
                        cross += pDiff * tplLuma[ty * pyrTw + tx];
                    }
                }

                if (patchVar < 25.0f || cross <= 0) continue;

                float corr = cross / (float) (Math.sqrt(patchVar * tplVar) + 1e-4);
                if (corr > bestCorr) {
                    bestCorr = corr;
                    candX = x * scale;
                    candY = y * scale;
                }
            }
        }

        if (bestCorr < 0.38f || candX < 0) {
            return null;
        }

        // 3. الفحص الجراحي الدقيق (Fine Refinement)
        int fineStartX = Math.max(boundX, boundX + candX - (scale * 2));
        int fineEndX = Math.min(boundX + limitW - tw, boundX + candX + (scale * 2));
        int fineStartY = Math.max(boundY, boundY + candY - (scale * 2));
        int fineEndY = Math.min(boundY + limitH - effectiveTh, boundY + candY + (scale * 2));

        Point finalPoint = null;
        double maxFinalSim = 0.0;

        for (int fy = fineStartY; fy <= fineEndY; fy++) {
            for (int fx = fineStartX; fx <= fineEndX; fx++) {
                double sim = compareSubRegionStrict(screen, template, fx, fy, colorTolerance, ignoreBadge);
                if (sim > maxFinalSim) {
                    maxFinalSim = sim;
                    finalPoint = new Point(fx, fy); // إرجاع أعلى اليسار لكي يضيف الفلوتي نصف الأبعاد بدقة
                }
            }
        }

        if (maxFinalSim >= minThreshold && finalPoint != null) {
            return finalPoint;
        }

        return null;
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY, int colorTolerance, boolean ignoreBottomBadge) {
        if (screen == null || template == null || screen.isRecycled() || template.isRecycled()) return 0.0;

        int tw = template.getWidth();
        int th = template.getHeight();
        int effectiveHeight = ignoreBottomBadge ? (int) (th * 0.75) : th;

        int safeX = Math.max(0, Math.min(cropX, screen.getWidth() - tw));
        int safeY = Math.max(0, Math.min(cropY, screen.getHeight() - effectiveHeight));

        int total = tw * effectiveHeight;
        if (total <= 0) return 0.0;

        int[] screenPixels = new int[total];
        int[] templatePixels = new int[total];

        screen.getPixels(screenPixels, 0, tw, safeX, safeY, tw, effectiveHeight);
        template.getPixels(templatePixels, 0, tw, 0, 0, tw, effectiveHeight);

        int tolRGB = Math.max(15, Math.min(50, colorTolerance));

        int matchedColorPixels = 0;
        int step = (total > 20000) ? 2 : 1;
        int samples = 0;

        float pSum = 0, tSum = 0;

        for (int i = 0; i < total; i += step) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            int sr = (sc >> 16) & 0xFF, sg = (sc >> 8) & 0xFF, sb = sc & 0xFF;
            int tr = (tc >> 16) & 0xFF, tg = (tc >> 8) & 0xFF, tb = tc & 0xFF;

            if (Math.abs(sr - tr) <= tolRGB && Math.abs(sg - tg) <= tolRGB && Math.abs(sb - tb) <= tolRGB) {
                matchedColorPixels++;
            }

            pSum += (sr * 77 + sg * 151 + sb * 28) >> 8;
            tSum += (tr * 77 + tg * 151 + tb * 28) >> 8;
            samples++;
        }

        if (samples == 0) return 0.0;

        float pMean = pSum / samples;
        float tMean = tSum / samples;

        float pVar = 0, tVar = 0, cross = 0;
        for (int i = 0; i < total; i += step) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            float pLum = (((sc >> 16) & 0xFF) * 77 + ((sc >> 8) & 0xFF) * 151 + (sc & 0xFF) * 28) >> 8;
            float tLum = (((tc >> 16) & 0xFF) * 77 + ((tc >> 8) & 0xFF) * 151 + (tc & 0xFF) * 28) >> 8;

            float pDiff = pLum - pMean;
            float tDiff = tLum - tMean;

            pVar += pDiff * pDiff;
            tVar += tDiff * tDiff;
            cross += pDiff * tDiff;
        }

        if (tVar > 80.0f && pVar < 25.0f) {
            return 5.0;
        }

        double colorRatio = ((double) matchedColorPixels / samples);
        double corr = 0.0;
        if (pVar > 10.0f && tVar > 10.0f) {
            corr = Math.max(0.0, cross / (Math.sqrt(pVar * tVar) + 1e-4));
        }

        double finalScore = (corr * 0.65 + colorRatio * 0.35) * 100.0;
        return Math.min(100.0, finalScore);
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        return compareSubRegionStrict(screen, template, cropX, cropY, 30, false);
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        return scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, -1, -1, minThreshold, 30, false);
    }
}
