package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;

/**
 * محرك البحث والتحقق الصارم فائق الدقة (Macrorify-Grade Strict Vision Engine)
 * يعتمد على مطابقة مصفوفة التردد العالي للنصوص (High-Frequency Text & Edge Bottleneck)
 * مع ارتباط الألوان ثلاثي القنوات (Multi-Channel ZNCC) وحظر القفز العشوائي في وضع CAPTURED.
 */
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
            searchX = Math.max(0, expX - 25);
            searchY = Math.max(0, expY - 25);
            searchW = action.getCropW() + 50;
            searchH = action.getCropH() + 50;
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

        // استخراج النسبة والتسامح الحقيقي من القالب أو الأكشن بتزامن كامل
        double threshold = action.getSimilarity() > 0 ? action.getSimilarity() : 70.0;
        int colorTolerance = 30;
        boolean ignoreBadge = false;

        if (!action.getTemplatePool().isEmpty()) {
            TemplateItem primary = action.getTemplatePool().get(0);
            if (primary.getSimilarity() > 0) {
                threshold = primary.getSimilarity();
            }
            colorTolerance = primary.getColorTolerance();
            ignoreBadge = primary.isIgnoreLevelBadge();
        }

        return scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, expX, expY, threshold, colorTolerance, ignoreBadge);
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

        // 1. الفحص الصارم لوضع Captured Location
        if (expectedX >= 0 && expectedY >= 0) {
            int testX = Math.max(boundX, Math.min(sw - tw, expectedX));
            int testY = Math.max(boundY, Math.min(sh - effectiveTh, expectedY));

            double directSim = matchTemplateScore(screen, template, testX, testY, colorTolerance, ignoreBadge);
            if (directSim >= minThreshold) {
                return new Point(testX, testY);
            }

            // فحص موضعي طفيف (±16 بكسل) لمعالجة اهتزاز رسم الشاشة البسيط
            Point localBest = null;
            double localMax = 0.0;
            for (int dy = -16; dy <= 16; dy += 2) {
                for (int dx = -16; dx <= 16; dx += 2) {
                    int curX = testX + dx;
                    int curY = testY + dy;
                    if (curX >= boundX && curX + tw <= sw && curY >= boundY && curY + effectiveTh <= sh) {
                        double s = matchTemplateScore(screen, template, curX, curY, colorTolerance, ignoreBadge);
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

            // عزل تام: وضع CAPTURED مخصص لمكان الزر فقط؛ إذا لم يتطابق مكانه يرفض فوراً ولا يبحث عشوائياً في الشاشة
            return null;
        }

        // 2. الهرم متعدد المقاييس (Pyramid ZNCC) لوضعي Full Screen و Custom Region
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
                int c = tplColors[(y * scale) * tw + (x * scale)];
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

        if (bestCorr < 0.45f || candX < 0) {
            return null;
        }

        // 3. التدقيق الجراحي الأصلي بدقة 1 بكسل
        int fineStartX = Math.max(boundX, boundX + candX - (scale * 2));
        int fineEndX = Math.min(boundX + limitW - tw, boundX + candX + (scale * 2));
        int fineStartY = Math.max(boundY, boundY + candY - (scale * 2));
        int fineEndY = Math.min(boundY + limitH - effectiveTh, boundY + candY + (scale * 2));

        Point finalPoint = null;
        double maxFinalSim = 0.0;

        for (int fy = fineStartY; fy <= fineEndY; fy++) {
            for (int fx = fineStartX; fx <= fineEndX; fx++) {
                double sim = matchTemplateScore(screen, template, fx, fy, colorTolerance, ignoreBadge);
                if (sim > maxFinalSim) {
                    maxFinalSim = sim;
                    finalPoint = new Point(fx, fy);
                }
            }
        }

        if (maxFinalSim >= minThreshold && finalPoint != null) {
            return finalPoint;
        }

        return null;
    }

    /**
     * حساب نسبة التطابق الدقيقة الصارمة بالملي (Zero-Tolerance Text & Color Matcher)
     */
    public static double matchTemplateScore(Bitmap screen, Bitmap template, int cropX, int cropY, int colorTolerance, boolean ignoreBottomBadge) {
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

        // 1. حساب المتوسطات لقنوات الألوان (RGB) والإضاءة (Luma)
        double rSumS = 0, gSumS = 0, bSumS = 0, lumSumS = 0;
        double rSumT = 0, gSumT = 0, bSumT = 0, lumSumT = 0;

        float[] sLum = new float[total];
        float[] tLum = new float[total];

        for (int i = 0; i < total; i++) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            int sr = (sc >> 16) & 0xFF, sg = (sc >> 8) & 0xFF, sb = sc & 0xFF;
            int tr = (tc >> 16) & 0xFF, tg = (tc >> 8) & 0xFF, tb = tc & 0xFF;

            float sl = (sr * 77f + sg * 151f + sb * 28f) / 256f;
            float tl = (tr * 77f + tg * 151f + tb * 28f) / 256f;

            sLum[i] = sl;
            tLum[i] = tl;

            rSumS += sr; gSumS += sg; bSumS += sb; lumSumS += sl;
            rSumT += tr; gSumT += tg; bSumT += tb; lumSumT += tl;
        }

        double invN = 1.0 / total;
        double rMeanS = rSumS * invN, gMeanS = gSumS * invN, bMeanS = bSumS * invN;
        double rMeanT = rSumT * invN, gMeanT = gSumT * invN, bMeanT = bSumT * invN;
        double lumMeanS = lumSumS * invN, lumMeanT = lumSumT * invN;

        // 2. تباين الألوان ثلاثي القنوات والارتباط المتبادل
        double varS = 0, varT = 0, crossColor = 0;
        double absErrSum = 0;

        for (int i = 0; i < total; i++) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            double drS = ((sc >> 16) & 0xFF) - rMeanS;
            double dgS = ((sc >> 8) & 0xFF) - gMeanS;
            double dbS = (sc & 0xFF) - bMeanS;

            double drT = ((tc >> 16) & 0xFF) - rMeanT;
            double dgT = ((tc >> 8) & 0xFF) - gMeanT;
            double dbT = (tc & 0xFF) - bMeanT;

            varS += drS * drS + dgS * dgS + dbS * dbS;
            varT += drT * drT + dgT * dgT + dbT * dbT;
            crossColor += drS * drT + dgS * dgT + dbT * dbT;

            absErrSum += Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF))
                       + Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF))
                       + Math.abs((sc & 0xFF) - (tc & 0xFF));
        }

        double corrColor = 0.0;
        if (varS > 10.0 && varT > 10.0) {
            corrColor = Math.max(0.0, crossColor / (Math.sqrt(varS * varT) + 1e-4));
        }

        // 3. فحص التردد العالي لحواف وتفاصيل النص (Text/Edge Strict Bottleneck)
        double detCross = 0, detVarS = 0, detVarT = 0, detAbsErr = 0;
        int detCount = 0;

        for (int y = 1; y < effectiveHeight - 1; y++) {
            int yOffset = y * tw;
            for (int x = 1; x < tw - 1; x++) {
                int idx = yOffset + x;

                float gxT = tLum[idx + 1] - tLum[idx - 1];
                float gyT = tLum[idx + tw] - tLum[idx - tw];
                float gradT = Math.abs(gxT) + Math.abs(gyT);

                float gxS = sLum[idx + 1] - sLum[idx - 1];
                float gyS = sLum[idx + tw] - sLum[idx - tw];
                float gradS = Math.abs(gxS) + Math.abs(gyS);

                // بكسل حافة/كتابة داخل الزر
                if (gradT > 18.0f || gradS > 18.0f) {
                    detCount++;
                    double dlS = sLum[idx] - lumMeanS;
                    double dlT = tLum[idx] - lumMeanT;

                    detVarS += dlS * dlS;
                    detVarT += dlT * dlT;
                    detCross += dlS * dlT;

                    int sc = screenPixels[idx];
                    int tc = templatePixels[idx];
                    detAbsErr += Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF))
                               + Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF))
                               + Math.abs((sc & 0xFF) - (tc & 0xFF));
                }
            }
        }

        double scoreDetail = corrColor;
        if (detCount >= 16 && detVarS > 10.0 && detVarT > 10.0) {
            scoreDetail = Math.max(0.0, detCross / (Math.sqrt(detVarS * detVarT) + 1e-4));
        }

        // 4. معامل دقة الألوان (Color Fidelity)
        double maeAll = (absErrSum / (total * 3.0)) / 255.0;
        double maeDet = detCount >= 16 ? (detAbsErr / (detCount * 3.0)) / 255.0 : maeAll;
        int safeTol = Math.max(15, Math.min(50, colorTolerance));
        double colorFactor = Math.max(0.0, 1.0 - (maeAll * 0.3 + maeDet * 0.7) * (30.0 / safeTol));

        // 5. الاختناق البنيوي الصارم (إذا تغير النص تسقط النتيجة كلياً ولا ترفعها الخلفية)
        double combCorr = (detCount >= 16) ? Math.min(corrColor, scoreDetail) : corrColor;
        double finalScore = combCorr * 100.0 * colorFactor;

        // عقوبة إضافية صارمة إذا كان النص غير متطابق
        if (detCount >= 16 && scoreDetail < 0.85) {
            double penalty = scoreDetail / 0.85;
            finalScore *= penalty;
        }

        return Math.min(100.0, Math.max(0.0, finalScore));
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY, int colorTolerance, boolean ignoreBottomBadge) {
        return matchTemplateScore(screen, template, cropX, cropY, colorTolerance, ignoreBottomBadge);
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        return matchTemplateScore(screen, template, cropX, cropY, 30, false);
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        return scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, -1, -1, minThreshold, 30, false);
    }
}
