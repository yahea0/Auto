package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * محرك البحث والتحقق فائق الدقة المطور (Macrorify-Grade Strict Vision Engine)
 * يدعم بدقة كاملة: Captured Location و Custom Region و Full Screen
 * مزود بنظام فحص المرشحات المتعددة (Multi-Candidate Adaptive ZNCC)
 */
public class VisionEngine {

    public static Point findActionTarget(Bitmap screen, Bitmap template, Action action) {
        if (screen == null || template == null || action == null || screen.isRecycled() || template.isRecycled()) {
            return null;
        }

        String rawMode = action.getDetectLocationMode();
        if (rawMode == null) rawMode = "CAPTURED";
        String modeUpper = rawMode.trim().toUpperCase();

        int sw = screen.getWidth();
        int sh = screen.getHeight();

        int searchX = 0, searchY = 0, searchW = sw, searchH = sh;
        int expX = -1, expY = -1;

        // 1. تحديد أبعاد البحث حسب الوضع بشكل مرن ودقيق
        if (modeUpper.contains("CAPTUR")) {
            expX = action.getCropX();
            expY = action.getCropY();
            searchX = Math.max(0, expX - 35);
            searchY = Math.max(0, expY - 35);
            searchW = Math.min(sw - searchX, action.getCropW() + 70);
            searchH = Math.min(sh - searchY, action.getCropH() + 70);
        } else if (modeUpper.contains("CUSTOM")) {
            int cx = action.getCustomRegionX();
            int cy = action.getCustomRegionY();
            int cw = action.getCustomRegionW();
            int ch = action.getCustomRegionH();

            if (cw > 10 && ch > 10) {
                searchX = Math.max(0, Math.min(cx, sw - 10));
                searchY = Math.max(0, Math.min(cy, sh - 10));
                searchW = Math.min(sw - searchX, cw);
                searchH = Math.min(sh - searchY, ch);
            } else {
                // في حال لم يتم تحديد أبعاد صالحة، نبحث في كامل الشاشة كإجراء أمان
                searchX = 0; searchY = 0; searchW = sw; searchH = sh;
            }
        } else {
            // وضع Full Screen
            searchX = 0;
            searchY = 0;
            searchW = sw;
            searchH = sh;
        }

        // استخراج النسبة والتسامح الحقيقي من القالب أو الأكشن
        double threshold = action.getSimilarity() > 0 ? action.getSimilarity() : 70.0;
        int colorTolerance = 30;
        boolean ignoreBadge = false;

        if (action.getTemplatePool() != null && !action.getTemplatePool().isEmpty()) {
            TemplateItem primary = action.getTemplatePool().get(0);
            if (primary.getSimilarity() > 0) {
                threshold = primary.getSimilarity();
            }
            colorTolerance = primary.getColorTolerance();
            ignoreBadge = primary.isIgnoreLevelBadge();
        }

        // فحص القالب الأساسي
        Point match = scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, expX, expY, threshold, colorTolerance, ignoreBadge);
        if (match != null) return match;

        // فحص القوالب البديلة إن وجدت في مصفوفة التدريب (Alternate Templates)
        if (action.getTemplatePool() != null && action.getTemplatePool().size() > 1) {
            for (int i = 1; i < action.getTemplatePool().size(); i++) {
                TemplateItem item = action.getTemplatePool().get(i);
                if (item.getImagePath() != null) {
                    try {
                        Bitmap altTpl = BitmapFactory.decodeFile(item.getImagePath());
                        if (altTpl != null) {
                            Point altMatch = scanAndFindTemplate(screen, altTpl, searchX, searchY, searchW, searchH, expX, expY,
                                    item.getSimilarity() > 0 ? item.getSimilarity() : threshold,
                                    item.getColorTolerance(),
                                    item.isIgnoreLevelBadge());
                            altTpl.recycle();
                            if (altMatch != null) return altMatch;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return null;
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

        // ==========================================
        // 1. الفحص الموضعي الصارم لوضع Captured Location
        // ==========================================
        if (expectedX >= 0 && expectedY >= 0) {
            int testX = Math.max(boundX, Math.min(sw - tw, expectedX));
            int testY = Math.max(boundY, Math.min(sh - effectiveTh, expectedY));

            double directSim = matchTemplateScore(screen, template, testX, testY, colorTolerance, ignoreBadge);
            if (directSim >= minThreshold) {
                return new Point(testX, testY);
            }

            // فحص موضعي طفيف (±20 بكسل) لمعالجة اهتزاز رسم الشاشة البسيط
            Point localBest = null;
            double localMax = 0.0;
            for (int dy = -20; dy <= 20; dy += 2) {
                for (int dx = -20; dx <= 20; dx += 2) {
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

            return null;
        }

        // ==========================================
        // 2. الهرم البصري الذكي لوضعي Custom Region و Full Screen
        // ==========================================
        int scale = (limitW > 600 && limitH > 600) ? 4 : 2;
        if (tw / scale < 8 || effectiveTh / scale < 8) {
            scale = 2;
        }

        int pyrSw = limitW / scale;
        int pyrSh = limitH / scale;
        int pyrTw = Math.max(2, tw / scale);
        int pyrTh = Math.max(2, effectiveTh / scale);

        if (pyrSw < pyrTw || pyrSh < pyrTh) return null;

        // استخراج الإضاءة للقالب
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

        // استخراج منطقة البحث وتدرج الإضاءة للشاشة بالكامل (مع إصلاح الخطأ السابق pyrSw)
        int[] screenRoi = new int[limitW * limitH];
        screen.getPixels(screenRoi, 0, limitW, boundX, boundY, limitW, limitH);

        float[] screenLuma = new float[pyrSw * pyrSh];
        for (int y = 0; y < pyrSh; y++) {
            int rowOffsetRoi = (y * scale) * limitW;
            int rowOffsetLuma = y * pyrSw;
            for (int x = 0; x < pyrSw; x++) { // تصحيح: مسح كامل عرض المنطقة وليس القالب فقط!
                int c = screenRoi[rowOffsetRoi + (x * scale)];
                screenLuma[rowOffsetLuma + x] = (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 151 + (c & 0xFF) * 28) >> 8;
            }
        }

        // تجميع أفضل المرشحات المتعددة (Multi-Candidates) لمنع الوقوع في فخ المرشح الخاطئ
        class Candidate implements Comparable<Candidate> {
            int x, y;
            float score;
            Candidate(int x, int y, float score) { this.x = x; this.y = y; this.score = score; }
            @Override
            public int compareTo(Candidate o) { return Float.compare(o.score, this.score); }
        }

        List<Candidate> candidates = new ArrayList<>();
        int coarseStep = (scale == 4) ? 2 : 1;

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

                if (patchVar < 20.0f || cross <= 0) continue;

                float corr = cross / (float) (Math.sqrt(patchVar * tplVar) + 1e-4);
                if (corr >= 0.38f) {
                    candidates.add(new Candidate(x * scale, y * scale, corr));
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // ترتيب المرشحات تنازلياً حسب النسبة
        Collections.sort(candidates);

        // ==========================================
        // 3. التدقيق الجراحي الأصلي بدقة 1 بكسل لأفضل المرشحات
        // ==========================================
        int testedCount = 0;
        int maxCandidatesToTest = Math.min(8, candidates.size());
        int searchRadius = scale * coarseStep + 4; // تغطية كامل الفجوة بين خطوات الهرم (±12px)

        Point bestMatchPoint = null;
        double maxFinalSim = 0.0;

        for (Candidate cand : candidates) {
            testedCount++;
            int fineStartX = Math.max(boundX, boundX + cand.x - searchRadius);
            int fineEndX = Math.min(boundX + limitW - tw, boundX + cand.x + searchRadius);
            int fineStartY = Math.max(boundY, boundY + cand.y - searchRadius);
            int fineEndY = Math.min(boundY + limitH - effectiveTh, boundY + cand.y + searchRadius);

            for (int fy = fineStartY; fy <= fineEndY; fy++) {
                for (int fx = fineStartX; fx <= fineEndX; fx++) {
                    double sim = matchTemplateScore(screen, template, fx, fy, colorTolerance, ignoreBadge);
                    if (sim > maxFinalSim) {
                        maxFinalSim = sim;
                        bestMatchPoint = new Point(fx, fy);
                    }
                    if (maxFinalSim >= Math.max(minThreshold, 85.0)) {
                        return bestMatchPoint;
                    }
                }
            }

            if (maxFinalSim >= minThreshold && bestMatchPoint != null) {
                return bestMatchPoint;
            }

            if (testedCount >= maxCandidatesToTest) break;
        }

        if (maxFinalSim >= minThreshold && bestMatchPoint != null) {
            return bestMatchPoint;
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
            crossColor += drS * drT + dgS * drT + dbS * dbT;

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

        // 5. النتيجة النهائية الموزونة
        double combCorr = (detCount >= 16) ? Math.min(corrColor, scoreDetail) : corrColor;
        double finalScore = combCorr * 100.0 * colorFactor;

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
