package com.example.myautomationapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Action implements Cloneable, Serializable {
    private String type; // "Click (x, y)", "Click Image", "Click Text", "Check Color", "Swipe", etc.
    private String detail;
    private int x;
    private int y;
    
    // دقة الشاشة المرجعية للتحجيم التلقائي (Base Resolution Scaling)
    private int baseScreenWidth = 1080;
    private int baseScreenHeight = 2400;

    private int delayBeforeMs = 0;
    private int delayAfterMs = 500;
    
    private String scalingAlgorithm = "Aspect Ratio";
    private String clickStyle = "Single Click"; // "Single Click", "Double Click", "Long Click"
    
    // أنماط النقر داخل الهدف (Target Click Strategy)
    private String targetClickMode = "Center"; // "Center", "Random Area", "Offset"
    private int targetRandomRadius = 8; // نطاق العشوائية بالبكسل

    // الشروط والمناطق
    private boolean hasCondition = false;
    private String conditionType = "No Condition"; // "Image Appear", "Text Appear", "Color Match"
    private String imageName = "img_1";
    private String imagePath = null;
    private int similarity = 70;
    private boolean isNotAppear = false;
    
    // إحداثيات ومقاسات المنطقة الأصلية
    private int cropX, cropY, cropW, cropH;

    // نمط منطقة الفحص: "CAPTURED", "CUSTOM", "FULL_SCREEN"
    private String detectLocationMode = "CAPTURED";
    private int customRegionX, customRegionY, customRegionW, customRegionH;
    private int searchMarginPx = 40; // هامش البحث المرن لوضع Captured

    // ميزة مهلة الانتظار المشروطة مثل Macrorify (Timeout Loop)
    private int timeoutSeconds = 5; 

    // فحص النصوص (OCR)
    private String targetText = "";
    private boolean textCaseSensitive = false;

    // فحص الألوان (Color Check)
    private int targetColor = 0;
    private int colorTolerance = 15;

    // قائمة القوالب المتطورة للتدريب المتعدد
    private List<TemplateItem> templatePool = new ArrayList<>();
    private boolean isMultiTargetEnabled = true;

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

    // Getters & Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getBaseScreenWidth() { return baseScreenWidth; }
    public void setBaseResolution(int width, int height) {
        this.baseScreenWidth = width;
        this.baseScreenHeight = height;
    }
    public int getBaseScreenHeight() { return baseScreenHeight; }

    public int getDelayBeforeMs() { return delayBeforeMs; }
    public void setDelayBeforeMs(int delayBeforeMs) { this.delayBeforeMs = delayBeforeMs; }

    public int getDelayAfterMs() { return delayAfterMs; }
    public void setDelayAfterMs(int delayAfterMs) { this.delayAfterMs = delayAfterMs; }

    public String getScalingAlgorithm() { return scalingAlgorithm; }
    public void setScalingAlgorithm(String scalingAlgorithm) { this.scalingAlgorithm = scalingAlgorithm; }

    public String getClickStyle() { return clickStyle; }
    public void setClickStyle(String clickStyle) { this.clickStyle = clickStyle; }

    public String getTargetClickMode() { return targetClickMode; }
    public void setTargetClickMode(String mode) { this.targetClickMode = mode; }

    public int getTargetRandomRadius() { return targetRandomRadius; }
    public void setTargetRandomRadius(int r) { this.targetRandomRadius = r; }

    public boolean hasCondition() { return hasCondition; }
    public void setHasCondition(boolean hasCondition) { this.hasCondition = hasCondition; }

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { 
        this.imagePath = imagePath;
        syncPrimaryTemplate();
    }

    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }

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
    public void setDetectLocationMode(String mode) { this.detectLocationMode = mode; }

    public int getSearchMarginPx() { return searchMarginPx; }
    public void setSearchMarginPx(int margin) { this.searchMarginPx = margin; }

    public int getCustomRegionX() { return customRegionX; }
    public int getCustomRegionY() { return customRegionY; }
    public int getCustomRegionW() { return customRegionW; }
    public int getCustomRegionH() { return customRegionH; }

    public void setCustomRegion(int x, int y, int w, int h) {
        this.customRegionX = x; this.customRegionY = y; this.customRegionW = w; this.customRegionH = h;
    }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int seconds) { this.timeoutSeconds = seconds; }

    public String getTargetText() { return targetText; }
    public void setTargetText(String targetText) { this.targetText = targetText; }

    public boolean isTextCaseSensitive() { return textCaseSensitive; }
    public void setTextCaseSensitive(boolean sensitive) { this.textCaseSensitive = sensitive; }

    public int getTargetColor() { return targetColor; }
    public void setTargetColor(int targetColor) { this.targetColor = targetColor; }

    public int getColorTolerance() { return colorTolerance; }
    public void setColorTolerance(int tolerance) { this.colorTolerance = tolerance; }

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
        if (item != null) templatePool.add(item);
    }

    private void syncPrimaryTemplate() {
        if (imagePath != null && templatePool.isEmpty()) {
            TemplateItem primary = new TemplateItem("primary", imagePath, imageName);
            primary.setSimilarity(similarity);
            templatePool.add(primary);
        }
    }

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
            return this;
        }
    }
}
