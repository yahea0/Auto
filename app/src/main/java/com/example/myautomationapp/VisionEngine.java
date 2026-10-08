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

        int matched = 0;
        int total = screenPixels.length;

        for (int i = 0; i < total; i += 2) {
            int sc = screenPixels[i]; int tc = templatePixels[i];
            int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
            int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
            int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

            if (diffR < colorTolerance && diffG < colorTolerance && diffB < colorTolerance) {
                matched++;
            }
        }

        int sampledTotal = (total + 1) / 2;
        if (sampledTotal == 0) return 0.0;
        return ((double) matched / sampledTotal) * 100.0;
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        return compareSubRegionStrict(screen, template, cropX, cropY, 30, false);
    }

    /**
     * التحقق الصارم من وجود العلامة المميزة المرجعية (Landmark Anchor) بجوار الهدف في الموضع النسبي المحدد
     */
    public static boolean verifyAnchorAtRelativeOffset(Bitmap screen, Bitmap anchorBmp, int targetX, int targetY, int relX, int relY, int tolerance, double minThresh) {
        if (screen == null || anchorBmp == null) return false;

        int expectedX = targetX + relX;
        int expectedY = targetY + relY;

        for (int dy = -6; dy <= 6; dy += 2) {
            for (int dx = -6; dx <= 6; dx += 2) {
                double sim = compareSubRegionStrict(screen, anchorBmp, expectedX + dx, expectedY + dy, tolerance, false);
                if (sim >= minThresh) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * المسح الفائق مع القفل المكاني والتحقق الإلزامي من وجود العلامة المميزة المرجعية
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

        // فحص الموضع المباشر أولاً
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
                                // شرط الأمان القاطع في لعبة الفاتحون: يجب وجود العلامة المميزة المرجعية!
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
        return scanAndFindTemplateWithSpatialLock(screen, template, null, searchX, searchY, searchW, searchH, searchX, searchY, minThreshold, 30, false, false, 0, 0, 30, 70);
    }
}
