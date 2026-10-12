package com.example.myautomationapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Action implements Cloneable, Serializable {
    private String type; // "Click (x, y)", "Click Image", "Click Text", "Check Color", "Swipe", "Press Back", "Press Home", "Wait", "Stop Macro"
    private String detail;
    private int x;
    private int y;
    
    // دقة الشاشة المرجعية للتحجيم التلقائي عبر مختلف الأجهزة (Base Resolution)
    private int baseScreenWidth = 1080;
    private int baseScreenHeight = 2400;

    private int delayBeforeMs = 0;
    private int delayAfterMs = 500;
    
    private String scalingAlgorithm = "Aspect Ratio";
    private String clickStyle = "Single Click"; // "Single Click", "Double Click", "Triple Click", "Long Click"
    
    // أسلوب النقر داخل مساحة الهدف (لمنع كشف البوتات)
    private String targetClickMode = "Center"; // "Center", "Random Area", "Offset"
    private int targetRandomRadius = 8; // نصف قطر العشوائية بالبكسل

    // الشروط والمناطق
    private boolean hasCondition = false;
    private String conditionType = "No Condition"; // "No Condition", "Image Appear", "Text Appear", "Color Match"
    private String imageName = "img_1";
    private String imagePath = null;
    private int similarity = 70;
    private boolean isNotAppear = false;
    
    // إحداثيات ومقاسات المنطقة المقتطعة الأصلية
    private int cropX, cropY, cropW, cropH;

    // نمط منطقة الفحص: "CAPTURED", "CUSTOM", "FULL_SCREEN"
    private String detectLocationMode = "CAPTURED";
    private int customRegionX, customRegionY, customRegionW, customRegionH;
    private int searchMarginPx = 40; // هامش البحث المرن (Margin/Padding) حول موضع الاقتصاص

    // مهلة انتظار ظهور/اختفاء العنصر في المنطقة (Condition Timeout)
    private int timeoutSeconds = 5; 

    // محرك فحص النصوص بالذكاء الاصطناعي (OCR)
    private String targetText = "";
    private boolean textCaseSensitive = false;

    // محرك فحص لون البكسل (Color Match)
    private int targetColor = 0xFFFFFFFF;
    private int colorTolerance = 20;

    // قائمة القوالب المتطورة للتدريب المتعدد
    private List<TemplateItem> templatePool = new ArrayList<>();
    private boolean isMultiTargetEnabled = true;
    private boolean isMultiScaleEnabled = true;

    private int offsetX = 0;
    private int offsetY = 0;
    private boolean isDisabled = false;
    private String customName = null;

    public Action(String type, String detail) {
        this.type = type;
        this.detail = detail;
    }

    public Action(String type, String detail, int x, int y) {
        this.type = type;
        this.detail = detail;
        this.x = x;
        this.y = y;
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getBaseScreenWidth() { return baseScreenWidth; }
    public int getBaseScreenHeight() { return baseScreenHeight; }
    public void setBaseResolution(int width, int height) {
        this.baseScreenWidth = width;
        this.baseScreenHeight = height;
    }

    public int getDelayBeforeMs() { return delayBeforeMs; }
    public void setDelayBeforeMs(int delayBeforeMs) { this.delayBeforeMs = delayBeforeMs; }

    public int getDelayAfterMs() { return delayAfterMs; }
    public void setDelayAfterMs(int delayAfterMs) { this.delayAfterMs = delayAfterMs; }

    public int getDelayMs() { return delayAfterMs; }
    public void setDelayMs(int delayMs) { this.delayAfterMs = delayMs; }

    public String getScalingAlgorithm() { return scalingAlgorithm; }
    public void setScalingAlgorithm(String scalingAlgorithm) { this.scalingAlgorithm = scalingAlgorithm; }

    public String getClickStyle() { return clickStyle; }
    public void setClickStyle(String clickStyle) { this.clickStyle = clickStyle; }

    public String getTargetClickMode() { return targetClickMode; }
    public void setTargetClickMode(String targetClickMode) { this.targetClickMode = targetClickMode; }

    public int getTargetRandomRadius() { return targetRandomRadius; }
    public void setTargetRandomRadius(int targetRandomRadius) { this.targetRandomRadius = targetRandomRadius; }

    public boolean hasCondition() { return hasCondition; }
    public void setHasCondition(boolean hasCondition) { this.hasCondition = hasCondition; }

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }

    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { 
        this.imagePath = imagePath;
        syncPrimaryTemplate();
    }

    public int getSimilarity() { 
        if (templatePool != null && !templatePool.isEmpty()) {
            return templatePool.get(0).getSimilarity();
        }
        return similarity; 
    }

    public void setSimilarity(int similarity) { 
        this.similarity = similarity;
        if (templatePool != null && !templatePool.isEmpty()) {
            templatePool.get(0).setSimilarity(similarity);
        }
    }

    public boolean isNotAppear() { return isNotAppear; }
    public void setNotAppear(boolean notAppear) { isNotAppear = notAppear; }

    public int getCropX() { return cropX; }
    public int getCropY() { return cropY; }
    public int getCropW() { return cropW; }
    public int getCropH() { return cropH; }

    public void setCropBounds(int x, int y, int w, int h) {
        this.cropX = x; this.cropY = y; this.cropW = w; this.cropH = h;
    }

    public String getDetectLocationMode() { return detectLocationMode; }
    public void setDetectLocationMode(String detectLocationMode) { this.detectLocationMode = detectLocationMode; }

    public int getSearchMarginPx() { return searchMarginPx; }
    public void setSearchMarginPx(int searchMarginPx) { this.searchMarginPx = searchMarginPx; }

    public int getCustomRegionX() { return customRegionX; }
    public int getCustomRegionY() { return customRegionY; }
    public int getCustomRegionW() { return customRegionW; }
    public int getCustomRegionH() { return customRegionH; }

    public void setCustomRegion(int x, int y, int w, int h) {
        this.customRegionX = x; this.customRegionY = y; this.customRegionW = w; this.customRegionH = h;
    }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getTargetText() { return targetText; }
    public void setTargetText(String targetText) { this.targetText = targetText; }

    public boolean isTextCaseSensitive() { return textCaseSensitive; }
    public void setTextCaseSensitive(boolean textCaseSensitive) { this.textCaseSensitive = textCaseSensitive; }

    public int getTargetColor() { return targetColor; }
    public void setTargetColor(int targetColor) { this.targetColor = targetColor; }

    public int getColorTolerance() { return colorTolerance; }
    public void setColorTolerance(int colorTolerance) { this.colorTolerance = colorTolerance; }

    public int getOffsetX() { return offsetX; }
    public void setOffsetX(int offsetX) { this.offsetX = offsetX; }

    public int getOffsetY() { return offsetY; }
    public void setOffsetY(int offsetY) { this.offsetY = offsetY; }

    public boolean isDisabled() { return isDisabled; }
    public void setDisabled(boolean disabled) { isDisabled = disabled; }

    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }

    public List<TemplateItem> getTemplatePool() { 
        syncPrimaryTemplate();
        return templatePool; 
    }

    public void addTemplateItem(TemplateItem item) {
        if (item != null) {
            templatePool.add(item);
        }
    }

    private void syncPrimaryTemplate() {
        if (imagePath != null && templatePool.isEmpty()) {
            TemplateItem primary = new TemplateItem("primary", imagePath, imageName);
            primary.setSimilarity(similarity);
            templatePool.add(primary);
        }
    }

    public boolean isMultiTargetEnabled() { return isMultiTargetEnabled; }
    public void setMultiTargetEnabled(boolean multiTargetEnabled) { isMultiTargetEnabled = multiTargetEnabled; }

    public boolean isMultiScaleEnabled() { return isMultiScaleEnabled; }
    public void setMultiScaleEnabled(boolean multiScaleEnabled) { isMultiScaleEnabled = multiScaleEnabled; }

    @Override
    public Action clone() {
        try {
            Action copy = (Action) super.clone();
            copy.templatePool = new ArrayList<>();
            for (TemplateItem item : this.templatePool) {
                copy.templatePool.add(item.clone());
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            Action copy = new Action(this.type, this.detail, this.x, this.y);
            copy.baseScreenWidth = this.baseScreenWidth;
            copy.baseScreenHeight = this.baseScreenHeight;
            copy.delayBeforeMs = this.delayBeforeMs;
            copy.delayAfterMs = this.delayAfterMs;
            copy.scalingAlgorithm = this.scalingAlgorithm;
            copy.clickStyle = this.clickStyle;
            copy.targetClickMode = this.targetClickMode;
            copy.targetRandomRadius = this.targetRandomRadius;
            copy.hasCondition = this.hasCondition;
            copy.conditionType = this.conditionType;
            copy.imageName = this.imageName;
            copy.imagePath = this.imagePath;
            copy.similarity = this.similarity;
            copy.isNotAppear = this.isNotAppear;
            copy.cropX = this.cropX; copy.cropY = this.cropY;
            copy.cropW = this.cropW; copy.cropH = this.cropH;
            copy.detectLocationMode = this.detectLocationMode;
            copy.customRegionX = this.customRegionX; copy.customRegionY = this.customRegionY;
            copy.customRegionW = this.customRegionW; copy.customRegionH = this.customRegionH;
            copy.searchMarginPx = this.searchMarginPx;
            copy.timeoutSeconds = this.timeoutSeconds;
            copy.targetText = this.targetText;
            copy.textCaseSensitive = this.textCaseSensitive;
            copy.targetColor = this.targetColor;
            copy.colorTolerance = this.colorTolerance;
            copy.offsetX = this.offsetX; copy.offsetY = this.offsetY;
            copy.isDisabled = this.isDisabled;
            copy.customName = this.customName;
            copy.templatePool = new ArrayList<>();
            for (TemplateItem item : this.templatePool) {
                copy.templatePool.add(item.clone());
            }
            copy.isMultiTargetEnabled = this.isMultiTargetEnabled;
            copy.isMultiScaleEnabled = this.isMultiScaleEnabled;
            return copy;
        }
    }
}
