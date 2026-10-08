package com.example.myautomationapp;

import java.io.Serializable;

public class TemplateItem implements Serializable, Cloneable {
    private String id;
    private String imagePath;
    private String name;
    private int similarity = 70;         // نسبة التطابق الخاصة بهذه الصورة
    private int colorTolerance = 30;      // تساهل أشعة الشمس والإضاءة (15 - 60)
    private boolean ignoreLevelBadge = false; // تجاهل رقم لفل المعسكر (1 إلى 10)
    private int width = 0;
    private int height = 0;

    public TemplateItem(String id, String imagePath, String name) {
        this.id = id;
        this.imagePath = imagePath;
        this.name = name;
    }

    public String getId() { return id; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSimilarity() { return similarity; }
    public void setSimilarity(int similarity) { this.similarity = similarity; }

    public int getColorTolerance() { return colorTolerance; }
    public void setColorTolerance(int colorTolerance) { this.colorTolerance = colorTolerance; }

    public boolean isIgnoreLevelBadge() { return ignoreLevelBadge; }
    public void setIgnoreLevelBadge(boolean ignoreLevelBadge) { this.ignoreLevelBadge = ignoreLevelBadge; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    @Override
    public TemplateItem clone() {
        try {
            return (TemplateItem) super.clone();
        } catch (CloneNotSupportedException e) {
            TemplateItem item = new TemplateItem(this.id, this.imagePath, this.name);
            item.similarity = this.similarity;
            item.colorTolerance = this.colorTolerance;
            item.ignoreLevelBadge = this.ignoreLevelBadge;
            item.width = this.width;
            item.height = this.height;
            return item;
        }
    }
}
