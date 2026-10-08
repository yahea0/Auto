package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;
import java.util.ArrayList;
import java.util.List;

public class VisionEngine {

    private static class Keypoint {
        int dx, dy;
        int r, g, b;
        Keypoint(int dx, int dy, int r, int g, int b) {
            this.dx = dx; this.dy = dy;
            this.r = r; this.g = g; this.b = b;
        }
    }

    private static List<Keypoint> extractFeaturePoints(Bitmap template, int maxPoints, boolean ignoreBottomBadge) {
        List<Keypoint> points = new ArrayList<>();
        int tw = template.getWidth();
        int th = template.getHeight();
        int effectiveHeight = ignoreBottomBadge ? (int) (th * 0.75) : th;

        int stepX = Math.max(1, tw / 10);
        int stepY = Math.max(1, effectiveHeight / 8);

        for (int y = 0; y < effectiveHeight && points.size() < maxPoints; y += stepY) {
            for (int x = 0; x < tw && points.size() < maxPoints; x += stepX) {
                int color = template.getPixel(x, y);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                points.add(new Keypoint(x, y, r, g, b));
            }
        }
        return points;
    }

    /**
     * معادلة التطابق البكسلي المستمر فائقة الدقة (Continuous Perceptual Metric)
     * تستجيب بدقة بالشعرة لأي تعديل في التسامح ونسبة التشابه دون انقطاع حاد
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

        // تحويل تسامح الظل والإضاءة إلى حد تدرج لوني ممتص
        double tolThreshold = (colorTolerance * 255.0) / 100.0;
        double totalScore = 0.0;
        int step = (total > 30000) ? 2 : 1;
        int samples = 0;

        for (int i = 0; i < total; i += step) {
            int sc = screenPixels[i];
            int tc = templatePixels[i];

            int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
            int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
            int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

            double pixelDiff = (diffR + diffG + diffB) / 3.0;

            // إذا كان التغير ضمن تسامح أشعة الشمس لا يُخصم أي شيء
            if (pixelDiff <= tolThreshold) {
                totalScore += 1.0;
            } else {
                // تدرج رياضي مستمر دون أي قفزات عشوائية
                double penalty = (pixelDiff - tolThreshold) / Math.max(1.0, 255.0 - tolThreshold);
                totalScore += Math.max(0.0, 1.0 - penalty);
            }
            samples++;
        }

        if (samples == 0) return 0.0;
        return (totalScore / samples) * 100.0;
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        return compareSubRegionStrict(screen, template, cropX, cropY, 35, false);
    }

    /**
     * قياس نسبة تطابق العلامة المميزة في موضعها النسبي بنافذة بحث مرنة (±20 بكسل)
     */
    public static double getAnchorMatchSimilarity(Bitmap screen, Bitmap anchorBmp, int targetCropX, int targetCropY, int relX, int relY, int tolerance) {
        if (screen == null || anchorBmp == null) return 0.0;

        int expectedX = targetCropX + relX;
        int expectedY = targetCropY + relY;

        double maxSim = 0.0;
        // مسح بنافذة ±20 بكسل لتحمل حركة الخريطة واهتزاز الكاميرا في لعبة الفاتحون
        for (int dy = -20; dy <= 20; dy += 3) {
            for (int dx = -20; dx <= 20; dx += 3) {
                double sim = compareSubRegionStrict(screen, anchorBmp, expectedX + dx, expectedY + dy, tolerance, false);
                if (sim > maxSim) {
                    maxSim = sim;
                }
            }
        }
        return maxSim;
    }

    public static boolean verifyAnchorAtRelativeOffset(Bitmap screen, Bitmap anchorBmp, int targetCropX, int targetCropY, int relX, int relY, int tolerance, double minThresh) {
        return getAnchorMatchSimilarity(screen, anchorBmp, targetCropX, targetCropY, relX, relY, tolerance) >= minThresh;
    }

    /**
     * المسح الفائق مع القفل المكاني والتحقق الإلزامي من وجود العلامة المميزة
     */
    public static Point scanAndFindTemplateWithSpatialLock(Bitmap screen, Bitmap template, Bitmap anchorBmp, int searchX, int searchY, int searchW, int searchH, int expectedX, int expectedY, double minThreshold, int colorTolerance, boolean ignoreLevelBadge, boolean hasAnchor, int relAnchorX, int relAnchorY, int anchorTol, double anchorThresh) {
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

        // 1. فحص فوري للموضع المباشر
        double directSim = compareSubRegionStrict(screen, template, boundX, boundY, colorTolerance, ignoreLevelBadge);
        if (directSim >= minThreshold) {
            if (!hasAnchor || verifyAnchorAtRelativeOffset(screen, anchorBmp, boundX, boundY, relAnchorX, relAnchorY, anchorTol, anchorThresh)) {
                return new Point(boundX, boundY);
            }
        }

        List<Keypoint> keypoints = extractFeaturePoints(template, 48, ignoreLevelBadge);
        int kpCount = keypoints.size();
        if (kpCount == 0) return null;

        int[] screenPixels = new int[sw * sh];
        screen.getPixels(screenPixels, 0, sw, 0, 0, sw, sh);

        int step = (tw > 70 && th > 70) ? 4 : 2;
        int maxAllowedMismatches = (int) (kpCount * (1.0 - (minThreshold / 100.0)) + 3);

        Point bestLockedPoint = null;
        double highestSpatialScore = 0.0;
        double screenDiagonal = Math.hypot(sw, sh);

        for (int y = boundY; y <= limitY; y += step) {
            for (int x = boundX; x <= limitX; x += step) {
                int mismatches = 0;
                int matched = 0;

                for (int k = 0; k < kpCount; k++) {
                    Keypoint kp = keypoints.get(k);
                    int pIndex = (y + kp.dy) * sw + (x + kp.dx);
                    if (pIndex >= screenPixels.length) break;

                    int sc = screenPixels[pIndex];
                    int diffR = Math.abs(((sc >> 16) & 0xFF) - kp.r);
                    int diffG = Math.abs(((sc >> 8) & 0xFF) - kp.g);
                    int diffB = Math.abs((sc & 0xFF) - kp.b);

                    if (diffR < colorTolerance && diffG < colorTolerance && diffB < colorTolerance) {
                        matched++;
                    } else {
                        mismatches++;
                        if (mismatches > maxAllowedMismatches) break;
                    }
                }

                if (mismatches <= maxAllowedMismatches) {
                    double score = ((double) matched / kpCount) * 100.0;
                    if (score >= Math.max(35.0, minThreshold - 20.0)) {
                        double distance = Math.hypot(x - expectedX, y - expectedY);
                        double distancePenalty = (distance / screenDiagonal) * 15.0;
                        double spatialScore = score - distancePenalty;

                        if (spatialScore > highestSpatialScore) {
                            double strictSim = compareSubRegionStrict(screen, template, x, y, colorTolerance, ignoreLevelBadge);
                            if (strictSim >= minThreshold) {
                                // شرط الأمان القاطع في لعبة الفاتحون
                                if (!hasAnchor || verifyAnchorAtRelativeOffset(screen, anchorBmp, x, y, relAnchorX, relAnchorY, anchorTol, anchorThresh)) {
                                    highestSpatialScore = spatialScore;
                                    bestLockedPoint = new Point(x, y);
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestLockedPoint;
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        return scanAndFindTemplateWithSpatialLock(screen, template, null, searchX, searchY, searchW, searchH, searchX, searchY, minThreshold, 35, false, false, 0, 0, 35, 70);
    }
}
