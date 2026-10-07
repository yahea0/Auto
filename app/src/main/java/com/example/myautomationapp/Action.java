package com.example.myautomationapp;

public class Action {
    private String type;
    private String detail;
    private int x;
    private int y;
    private int delayMs = 500;
    
    // بيانات شرط Macrorify البصري
    private boolean hasCondition = false;
    private String conditionType = "No Condition";
    private String imageName = "img_1";
    private int similarity = 70; // 70% افتراضي كما في مايكروفي
    private boolean isNotAppear = false; // false = [Appear], true = [Not Appear]
    private int cropX, cropY, cropW, cropH;

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
    public String getDetail() { return detail; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getDelayMs() { return delayMs; }
    public void setDelayMs(int delayMs) { this.delayMs = delayMs; }

    public boolean hasCondition() { return hasCondition; }
    public void setHasCondition(boolean hasCondition) { this.hasCondition = hasCondition; }

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }

    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }

    public int getSimilarity() { return similarity; }
    public void setSimilarity(int similarity) { this.similarity = similarity; }

    public boolean isNotAppear() { return isNotAppear; }
    public void setNotAppear(boolean notAppear) { isNotAppear = notAppear; }

    public void setCropBounds(int x, int y, int w, int h) {
        this.cropX = x; this.cropY = y; this.cropW = w; this.cropH = h;
    }
}
