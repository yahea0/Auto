package com.example.myautomationapp;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

public class MacroAiInspector {

    /**
     * توليد قائمة النسب المئوية المتاحة للضغط بنجاح مع فواصل (مثل: 75, 74, 73, 72...)
     */
    public static String generateAvailablePassingPercentages(double exactSim) {
        int maxPassing = (int) Math.floor(exactSim);
        if (maxPassing <= 0) return "لا توجد نسب نجاح (الصورة غير مطابقة نهائياً)";

        List<String> list = new ArrayList<>();
        // عرض أفضل النسب المتاحة القريبة من النسبة اللحظية
        for (int i = maxPassing; i >= Math.max(1, maxPassing - 8); i--) {
            list.add(String.valueOf(i));
        }
        return String.join(", ", list) + "%";
    }

    /**
     * تقديم أفضل نسبة موصى بها تضمن النقر المستقر
     */
    public static int getRecommendedSimilarity(double currentSimilarity) {
        if (currentSimilarity >= 98.0) {
            return 95;
        } else if (currentSimilarity >= 85.0) {
            return (int) (currentSimilarity - 4);
        } else if (currentSimilarity >= 60.0) {
            return (int) (currentSimilarity - 6);
        } else {
            return Math.max(1, (int) currentSimilarity);
        }
    }

    /**
     * تقرير تشخيصي شامل للنظام
     */
    public static String getSystemDiagnosticReport(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 فحص محرك الرؤية والذكاء الاصطناعي:\n\n");
        sb.append(AutoAccessibilityService.isRunning() ? "✅ محرك النقر: متصل وجاهز.\n" : "❌ محرك النقر: غير مفعل في إمكانية الوصول!\n");
        sb.append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context) ? "✅ النوافذ العائمة: مصرح لها.\n" : "❌ النوافذ العائمة: غير مصرح لها!\n");
        sb.append("✅ نظام الفحص الصارم: 16-Grid Matrix نشط.\n");
        sb.append("📊 عدد الأكشنات المجهزة: ").append(GlobalData.actionList.size()).append("\n");
        return sb.toString();
    }
}
