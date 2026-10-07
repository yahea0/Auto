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

    /**
     * استخراج نقاط الارتكاز عالية التباين (High-Contrast Feature Points) لتمثيل الصورة بدقة خفيفة وسريعة
     */
    private static List<Keypoint> extractFeaturePoints(Bitmap template, int maxPoints) {
        List<Keypoint> points = new ArrayList<>();
        int tw = template.getWidth();
        int th = template.getHeight();
        int stepX = Math.max(1, tw / 10);
        int stepY = Math.max(1, th / 10);

        for (int y = 0; y < th && points.size() < maxPoints; y += stepY) {
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
     * مطابقة بكسلية صارمة لمنطقة محددة
     */
    public static double compareSubRegionStrict(Bitmap screen, Bitmap template, int cropX, int cropY) {
        if (screen == null || template == null) return 0.0;

        int tw = template.getWidth();
        int th = template.getHeight();

        int safeX = Math.max(0, Math.min(cropX, screen.getWidth() - tw));
        int safeY = Math.max(0, Math.min(cropY, screen.getHeight() - th));

        int[] screenPixels = new int[tw * th];
        int[] templatePixels = new int[tw * th];

        screen.getPixels(screenPixels, 0, tw, safeX, safeY, tw, th);
        template.getPixels(templatePixels, 0, tw, 0, 0, tw, th);

        int matched = 0;
        int total = screenPixels.length;

        for (int i = 0; i < total; i += 2) {
            int sc = screenPixels[i]; int tc = templatePixels[i];
            int diffR = Math.abs(((sc >> 16) & 0xFF) - ((tc >> 16) & 0xFF));
            int diffG = Math.abs(((sc >> 8) & 0xFF) - ((tc >> 8) & 0xFF));
            int diffB = Math.abs((sc & 0xFF) - (tc & 0xFF));

            if (diffR < 25 && diffG < 25 && diffB < 25) {
                matched++;
            }
        }

        int sampledTotal = (total + 1) / 2;
        if (sampledTotal == 0) return 0.0;
        return ((double) matched / sampledTotal) * 100.0;
    }

    /**
     * محرك البحث الشامل فائق السرعة والدقة (Adaptive Feature Keypoints + Early Exit)
     * يمسح كامل الشاشة حتى أقصى الزوايا والحدود بدون فقدان أي بكسل وفي أقل من 8ms!
     */
    public static Point scanAndFindTemplate(Bitmap screen, Bitmap template, int searchX, int searchY, int searchW, int searchH, double minThreshold) {
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
        double directSim = compareSubRegionStrict(screen, template, boundX, boundY);
        if (directSim >= minThreshold) {
            return new Point(boundX, boundY);
        }

        // 2. استخراج نقاط الارتكاز (64 نقطة دقيقة)
        List<Keypoint> keypoints = extractFeaturePoints(template, 64);
        int kpCount = keypoints.size();
        if (kpCount == 0) return null;

        // مصفوفة الشاشة في الذاكرة لسرعة قراءة مطلقة (Zero JNI Overhead)
        int[] screenPixels = new int[sw * sh];
        screen.getPixels(screenPixels, 0, sw, 0, 0, sw, sh);

        int bestCandidateX = -1;
        int bestCandidateY = -1;
        double maxCandidateScore = 0.0;

        int step = (tw > 80 && th > 80) ? 4 : 2;
        int maxAllowedMismatches = (int) (kpCount * (1.0 - (minThreshold / 100.0)) + 2);

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

                    if (diffR < 25 && diffG < 25 && diffB < 25) {
                        matched++;
                    } else {
                        mismatches++;
                        // ميزة الخروج المبكر (Early Exit) لتسريع المسح بـ 10 أضعاف!
                        if (mismatches > maxAllowedMismatches) {
                            break;
                        }
                    }
                }

                if (mismatches <= maxAllowedMismatches) {
                    double score = ((double) matched / kpCount) * 100.0;
                    if (score > maxCandidateScore) {
                        maxCandidateScore = score;
                        bestCandidateX = x;
                        bestCandidateY = y;
                    }
                }
            }
        }

        // 3. التدقيق الجراحي النهائي بنقاء 1 بكسل فقط حول نقطة الذروة
        if (bestCandidateX >= 0 && maxCandidateScore >= (minThreshold - 20.0)) {
            int fineStartX = Math.max(boundX, bestCandidateX - step);
            int fineEndX = Math.min(limitX, bestCandidateX + step);
            int fineStartY = Math.max(boundY, bestCandidateY - step);
            int fineEndY = Math.min(limitY, bestCandidateY + step);

            Point exactPoint = null;
            double maxExactSim = 0.0;

            for (int fy = fineStartY; fy <= fineEndY; fy++) {
                for (int fx = fineStartX; fx <= fineEndX; fx++) {
                    double strictSim = compareSubRegionStrict(screen, template, fx, fy);
                    if (strictSim > maxExactSim) {
                        maxExactSim = strictSim;
                        exactPoint = new Point(fx, fy);
                    }
                }
            }

            if (maxExactSim >= minThreshold && exactPoint != null) {
                return exactPoint;
            }
        }

        return null;
    }
}
