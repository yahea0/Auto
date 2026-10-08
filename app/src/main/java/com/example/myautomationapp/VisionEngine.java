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
        
        // إذا كان خيار عزل اللفل مفعلاً، نتجاهل الـ 25% السفلية من الصورة (حيث يوجد رقم 10 أو 1)
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

            // تساهل أشعة الشمس اللوني
            if (diffR < colorTolerance && diffG < colorTolerance && diffB < colorTolerance) {
                matched++;
            }
        }

        int sampledTotal = (total + 1) / 2;
        if (sampledTotal == 0) return 0.0;
        return ((double) matched / sampledTotal) * 100.0;
    }

    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        return compareSubRegionStrict(screen, template, cropX, cropY, 28, false);
    }

    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
        List<Point> results = scanAndFindAllTemplates(screen, template, searchX, searchY, searchW, searchH, minThreshold, 28, false, 1);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * المسح الفائق متعدد الأهداف مع دعم تساهل الشمس وعزل أرقام اللفل
     */
    public static List<Point> scanAndFindAllTemplates(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold, int colorTolerance, boolean ignoreLevelBadge, int maxMatches) {
        List<Point> finalMatches = new ArrayList<>();
        if (screen == null || template == null) return finalMatches;

        int tw = template.getWidth();
        int th = template.getHeight();
        int sw = screen.getWidth();
        int sh = screen.getHeight();

        if (tw <= 0 || th <= 0 || sw < tw || sh < th) return finalMatches;

        int boundX = Math.max(0, Math.min(searchX, sw - tw));
        int boundY = Math.max(0, Math.min(searchY, sh - th));
        int limitX = Math.min(sw - tw, boundX + searchW);
        int limitY = Math.min(sh - th, boundY + searchH);

        // 1. فحص فوري للموضع المباشر
        double directSim = compareSubRegionStrict(screen, template, boundX, boundY, colorTolerance, ignoreLevelBadge);
        if (directSim >= minThreshold) {
            finalMatches.add(new Point(boundX, boundY));
            if (maxMatches == 1) return finalMatches;
        }

        List<Keypoint> keypoints = extractFeaturePoints(template, 48, ignoreLevelBadge);
        int kpCount = keypoints.size();
        if (kpCount == 0) return finalMatches;

        int[] screenPixels = new int[sw * sh];
        screen.getPixels(screenPixels, 0, sw, 0, 0, sw, sh);

        int step = (tw > 70 && th > 70) ? 4 : 2;
        int maxAllowedMismatches = (int) (kpCount * (1.0 - (minThreshold / 100.0)) + 3);
        int minDistance = Math.max(20, Math.min(tw, th) / 2);

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
                        boolean isAlreadyFound = false;
                        for (Point existing : finalMatches) {
                            if (Math.hypot(existing.x - x, existing.y - y) < minDistance) {
                                isAlreadyFound = true;
                                break;
                            }
                        }

                        if (!isAlreadyFound) {
                            double strictSim = compareSubRegionStrict(screen, template, x, y, colorTolerance, ignoreLevelBadge);
                            if (strictSim >= minThreshold) {
                                finalMatches.add(new Point(x, y));
                                if (finalMatches.size() >= maxMatches) {
                                    return finalMatches;
                                }
                            }
                        }
                    }
                }
            }
        }

        return finalMatches;
    }
}
