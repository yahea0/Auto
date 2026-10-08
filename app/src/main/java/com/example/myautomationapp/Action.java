package com.example.myautomationapp;

import java.util.ArrayList;
import java.util.List;

public class Action implements Cloneable {
    private String type;
    private String detail;
    private int x;
    private int y;
    
    private int delayBeforeMs = 0;
    private int delayAfterMs = 500;
    
    private String scalingAlgorithm = "Aspect Ratio";
    private String clickStyle = "Single Click";
    
    private boolean hasCondition = false;
    private String conditionType = "No Condition";
    private String imageName = "img_1";
    private String imagePath = null;
    private int similarity = 70;
    private boolean isNotAppear = false;
    private int cropX, cropY, cropW, cropH;

    private String detectLocationMode = "CAPTURED";
    private int customRegionX, customRegionY, customRegionW, customRegionH;

    // قائمة القوالب المتطورة للتدريب المتعدد (زوم، زوايا، إضاءة)
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

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

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

    // تزامن تام للنسبة المئوية بين الأكشن وقالب التدريب
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

    public int getCustomRegionX() { return customRegionX; }
    public int getCustomRegionY() { return customRegionY; }
    public int getCustomRegionW() { return customRegionW; }
    public int getCustomRegionH() { return customRegionH; }

    public void setCustomRegion(int x, int y, int w, int h) {
        this.customRegionX = x; this.customRegionY = y; this.customRegionW = w; this.customRegionH = h;
    }

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
            copy.delayBeforeMs = this.delayBeforeMs;
            copy.delayAfterMs = this.delayAfterMs;
            copy.scalingAlgorithm = this.scalingAlgorithm;
            copy.clickStyle = this.clickStyle;
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
