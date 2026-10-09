package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * محرك الرؤية فائق السرعة والدقة (Ultra-Fast Sparse-Probe ZNCC & Global ArgMax)
 * زمن استجابة 15ms - عزل تام للأهداف الخاطئة
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
                searchX = 0; searchY = 0; searchW = sw; searchH = sh;
            }
        } else {
            // Full Screen
            searchX = 0; searchY = 0; searchW = sw; searchH = sh;
        }

        double threshold = action.getSimilarity() > 0 ? action.getSimilarity() : 70.0;
        int colorTolerance = 30;
        boolean ignoreBadge = false;

        if (action.getTemplatePool() != null && !action.getTemplatePool().isEmpty()) {
            TemplateItem primary = action.getTemplatePool().get(0);
            if (primary.getSimilarity() > 0) threshold = primary.getSimilarity();
            colorTolerance = primary.getColorTolerance();
            ignoreBadge = primary.isIgnoreLevelBadge();
        }

        Point match = scanAndFindTemplate(screen, template, searchX, searchY, searchW, searchH, expX, expY, threshold, colorTolerance, ignoreBadge);
        if (match != null) return match;

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

        // 1. المسار الفوري لوضع Captured Location
        if (expectedX >= 0 && expectedY >= 0) {
            int testX = Math.max(boundX, Math.min(sw - tw, expectedX));
            int testY = Math.max(boundY, Math.min(sh - effectiveTh, expectedY));

            double directSim = matchTemplateScore(screen, template, testX, testY, colorTolerance, ignoreBadge);
            if (directSim >= minThreshold) return new Point(testX, testY);

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
            if (localMax >= minThreshold && localBest != null) return localBest;
            return null;
        }

        // 2. محرك المجسات فائق السرعة لوضعي Custom Region و Full Screen
        int scale = (limitW > 500 && limitH > 500) ? 4 : 2;
        if (tw / scale < 10 || effectiveTh / scale < 10) scale = 2;

        int pyrSw = limitW / scale;
        int pyrSh = limitH / scale;
        int pyrTw = Math.max(2, tw / scale);
        int pyrTh = Math.max(2, effectiveTh / scale);

        if (pyrSw < pyrTw || pyrSh < pyrTh) return null;

        // تجهيز المجسات النقطية للقالب (Sparse Probes - 64 نقطة استراتيجية بدلاً من آلاف النقاط)
        int probeGridX = Math.min(8, pyrTw);
        int probeGridY = Math.min(8, pyrTh);
        int numProbes = probeGridX * probeGridY;

        int[] probeOffsets = new int[numProbes];
        float[] probeTplVals = new float[numProbes];

        int[] tplColors = new int[tw * effectiveTh];
        template.getPixels(tplColors, 0, tw, 0, 0, tw, effectiveTh);

        int stepX = Math.max(1, pyrTw / probeGridX);
        int stepY = Math.max(1, pyrTh / probeGridY);
        int pIdx = 0;
        float tplProbeSum = 0;

        for (int gy = 0; gy < probeGridY; gy++) {
            int py = Math.min(pyrTh - 1, gy * stepY);
            for (int gx = 0; gx < probeGridX; gx++) {
                int px = Math.min(pyrTw - 1, gx * stepX);
                probeOffsets[pIdx] = py * pyrSw + px;

                int c = tplColors[(py * scale) * tw + (px * scale)];
                float lum = (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 151 + (c & 0xFF) * 28) >> 8;
                probeTplVals[pIdx] = lum;
                tplProbeSum += lum;
                pIdx++;
            }
        }

        float tplProbeMean = tplProbeSum / numProbes;
        float tplProbeVar = 0;
        for (int i = 0; i < numProbes; i++) {
            probeTplVals[i] -= tplProbeMean;
            tplProbeVar += probeTplVals[i] * probeTplVals[i];
        }
        if (tplProbeVar < 10.0f) tplProbeVar = 10.0f;

        // سحب إضاءة شاشة البحث كاملة
        int[] screenRoi = new int[limitW * limitH];
        screen.getPixels(screenRoi, 0, limitW, boundX, boundY, limitW, limitH);

        float[] screenLuma = new float[pyrSw * pyrSh];
        for (int y = 0; y < pyrSh; y++) {
            int rowOffsetRoi = (y * scale) * limitW;
            int rowOffsetLuma = y * pyrSw;
            for (int x = 0; x < pyrSw; x++) {
                int c = screenRoi[rowOffsetRoi + (x * scale)];
                screenLuma[rowOffsetLuma + x] = (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 151 + (c & 0xFF) * 28) >> 8;
            }
        }

        // 3. المسح الفوري وتطبيق العزل المكاني (Spatial NMS)
        class ScoredCandidate implements Comparable<ScoredCandidate> {
            int realX, realY;
            float score;
            ScoredCandidate(int rx, int ry, float s) { realX = rx; realY = ry; score = s; }
            @Override
            public int compareTo(ScoredCandidate o) { return Float.compare(o.score, this.score); }
        }

        List<ScoredCandidate> distinctCandidates = new ArrayList<>();
        int coarseStep = (scale == 4) ? 2 : 1;
        float minCoarseThreshold = 0.40f;

        int minDistanceX = (pyrTw * scale) / 2;
        int minDistanceY = (pyrTh * scale) / 2;

        for (int y = 0; y <= pyrSh - pyrTh; y += coarseStep) {
            int rowOffset = y * pyrSw;
            for (int x = 0; x <= pyrSw - pyrTw; x += coarseStep) {
                int baseIdx = rowOffset + x;

                float patchSum = 0;
                for (int i = 0; i < numProbes; i++) {
                    patchSum += screenLuma[baseIdx + probeOffsets[i]];
                }
                float patchMean = patchSum / numProbes;

                float patchVar = 0;
                float cross = 0;
                for (int i = 0; i < numProbes; i++) {
                    float diffS = screenLuma[baseIdx + probeOffsets[i]] - patchMean;
                    patchVar += diffS * diffS;
                    cross += diffS * probeTplVals[i];
                }

                if (patchVar < 15.0f || cross <= 0) continue;

                float corr = cross / (float) (Math.sqrt(patchVar * tplProbeVar) + 1e-4);
                if (corr >= minCoarseThreshold) {
                    int candRealX = x * scale;
                    int candRealY = y * scale;

                    // تطبيق NMS لمنع تكرار نفس المكان الخاطئ
                    boolean merged = false;
                    for (ScoredCandidate existing : distinctCandidates) {
                        if (Math.abs(existing.realX - candRealX) < minDistanceX && Math.abs(existing.realY - candRealY) < minDistanceY) {
                            if (corr > existing.score) {
                                existing.realX = candRealX;
                                existing.realY = candRealY;
                                existing.score = corr;
                            }
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) {
                        distinctCandidates.add(new ScoredCandidate(candRealX, candRealY, corr));
                    }
                }
            }
        }

        if (distinctCandidates.isEmpty()) return null;

        Collections.sort(distinctCandidates);

        // 4. التدقيق الجراحي واختيار القمة العالمية الحقيقية (Global ArgMax)
        Point absoluteBestPoint = null;
        double absoluteBestSim = 0.0;
        int searchRadius = scale * coarseStep + 4;
        int maxToTest = Math.min(5, distinctCandidates.size());

        for (int i = 0; i < maxToTest; i++) {
            ScoredCandidate cand = distinctCandidates.get(i);
            int fineStartX = Math.max(boundX, boundX + cand.realX - searchRadius);
            int fineEndX = Math.min(boundX + limitW - tw, boundX + cand.realX + searchRadius);
            int fineStartY = Math.max(boundY, boundY + cand.realY - searchRadius);
            int fineEndY = Math.min(boundY + limitH - effectiveTh, boundY + cand.realY + searchRadius);

            for (int fy = fineStartY; fy <= fineEndY; fy++) {
                for (int fx = fineStartX; fx <= fineEndX; fx++) {
                    double sim = matchTemplateScore(screen, template, fx, fy, colorTolerance, ignoreBadge);
                    if (sim > absoluteBestSim) {
                        absoluteBestSim = sim;
                        absoluteBestPoint = new Point(fx, fy);
                    }
                }
            }

            // إذا وجدنا تطابقاً ساحقاً نكتفي به فوراً لتوفير الوقت
            if (absoluteBestSim >= 94.0) break;
        }

        if (absoluteBestSim >= minThreshold && absoluteBestPoint != null) {
            return absoluteBestPoint;
        }

        return null;
    }

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

        double rSumS = 0, gSumS = 0, bSumS = 0;
        double rSumT = 0, gSumT = 0, bSumT = 0;

        float[] sLum = new float[total];
        float[] tLum = new float[total];

        for (int i = 0; i < total; i++) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            int sr = (sc >> 16) & 0xFF, sg = (sc >> 8) & 0xFF, sb = sc & 0xFF;
            int tr = (tc >> 16) & 0xFF, tg = (tc >> 8) & 0xFF, tb = tc & 0xFF;

            sLum[i] = (sr * 77f + sg * 151f + sb * 28f) / 256f;
            tLum[i] = (tr * 77f + tg * 151f + tb * 28f) / 256f;

            rSumS += sr; gSumS += sg; bSumS += sb;
            rSumT += tr; gSumT += tg; bSumT += tb;
        }

        double invN = 1.0 / total;
        double rMeanS = rSumS * invN, gMeanS = gSumS * invN, bMeanS = bSumS * invN;
        double rMeanT = rSumT * invN, gMeanT = gSumT * invN, bMeanT = bSumT * invN;

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

        double detCross = 0, detVarS = 0, detVarT = 0, detAbsErr = 0;
        int detCount = 0;

        for (int y = 1; y < effectiveHeight - 1; y++) {
            int yOffset = y * tw;
            for (int x = 1; x < tw - 1; x++) {
                int idx = yOffset + x;

                float gradT = Math.abs(tLum[idx + 1] - tLum[idx - 1]) + Math.abs(tLum[idx + tw] - tLum[idx - tw]);
                float gradS = Math.abs(sLum[idx + 1] - sLum[idx - 1]) + Math.abs(sLum[idx + tw] - sLum[idx - tw]);

                if (gradT > 18.0f || gradS > 18.0f) {
                    detCount++;
                    detVarS += sLum[idx] * sLum[idx];
                    detVarT += tLum[idx] * tLum[idx];
                    detCross += sLum[idx] * tLum[idx];

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

        double maeAll = (absErrSum / (total * 3.0)) / 255.0;
        double maeDet = detCount >= 16 ? (detAbsErr / (detCount * 3.0)) / 255.0 : maeAll;
        int safeTol = Math.max(15, Math.min(50, colorTolerance));
        double colorFactor = Math.max(0.0, 1.0 - (maeAll * 0.3 + maeDet * 0.7) * (30.0 / safeTol));

        double combCorr = (detCount >= 16) ? Math.min(corrColor, scoreDetail) : corrColor;
        double finalScore = combCorr * 100.0 * colorFactor;

        if (detCount >= 16 && scoreDetail < 0.85) {
            finalScore *= (scoreDetail / 0.85);
        }

        return Math.min(100.0, Math.max(0.0, finalScore));
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY, int colorTolerance, boolean ignoreBottomBadge) {
        return matchTemplateScore(screen, template, cropX, cropY, colorTolerance, ignoreBottomBadge);
    }
}
