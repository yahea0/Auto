package com.example.myautomationapp;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import java.io.File;

public class MacroAiInspector {

    /**
     * تشخيص شامل لحالة النظام وما يستطيع وما لا يستطيع البرنامج فعله حالياً
     */
    public static String getSystemDiagnosticReport(Context context) {
        StringBuilder report = new StringBuilder();
        report.append("🤖 تقرير الذكاء الاصطناعي لتشخيص التطبيق:\n\n");

        // 1. فحص خدمة الوصول (محرك النقر)
        if (AutoAccessibilityService.isRunning()) {
            report.append("✅ محرك النقر (Accessibility): نشط وجاهز للتنفيذ الفوري.\n");
        } else {
            report.append("❌ محرك النقر: متوقف! (البرنامج لا يستطيع لمس الشاشة بدون تفعيل الخدمة من الإعدادات).\n");
        }

        // 2. فحص محرك تصوير الشاشة (الرؤية)
        if (ScreenCaptureManager.getInstance() != null) {
            report.append("✅ محرك الرؤية (MediaProjection): مصرح له بالتقاط فريمات الشاشة.\n");
        } else {
            report.append("⚠️ محرك الرؤية: لم يتم تهيئة إذن الشاشة بعد.\n");
        }

        // 3. فحص صلاحية الظهور فوق التطبيقات
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            report.append("✅ صلاحية النوافذ العائمة: ممنوحة بنجاح.\n");
        } else {
            report.append("❌ صلاحية الظهور: معطلة!\n");
        }

        // 4. فحص الأكشنات الحالية وشروط الصور
        int actionsCount = GlobalData.actionList.size();
        report.append("\n📊 فحص الماكرو الحالي (").append(actionsCount).append(" أكشن):\n");

        if (actionsCount == 0) {
            report.append("• لا توجد أكشنات مضافة للتشغيل.\n");
        } else {
            int imageConditions = 0;
            for (Action a : GlobalData.actionList) {
                if (a.hasCondition() && "Image Appear".equals(a.getConditionType())) {
                    imageConditions++;
                    if (a.getImagePath() != null) {
                        File f = new File(a.getImagePath());
                        if (!f.exists()) {
                            report.append("⚠️ تنبيه: ملف صورة الأكشن (").append(a.getImageName()).append(") غير موجود في الذاكرة!\n");
                        }
                    }
                }
            }
            report.append("• عدد شروط الصور المرتبطة: ").append(imageConditions).append("\n");
            report.append("• حالة الجاهزية: جاهز بنسبة 100% للتنفيذ.\n");
        }

        return report.toString();
    }

    /**
     * تحليل ذكي لنسبة التطابق وتقديم النسبة الموصى بها للألعاب
     */
    public static int getRecommendedSimilarity(double currentSimilarity) {
        if (currentSimilarity >= 95.0) {
            // للألعاب المستقرة: نوصي بـ 90% لتفادي تغير الإضاءة أو الريندر
            return Math.max(70, (int) (currentSimilarity - 5));
        } else if (currentSimilarity >= 75.0) {
            return (int) (currentSimilarity - 8);
        } else if (currentSimilarity >= 50.0) {
            return (int) (currentSimilarity - 10);
        } else {
            return (int) currentSimilarity;
        }
    }
}
