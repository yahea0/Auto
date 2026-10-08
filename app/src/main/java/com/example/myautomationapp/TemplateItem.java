package com.example.myautomationapp;

import java.io.Serializable;

public class TemplateItem implements Serializable, Cloneable {
    private String id;
    private String imagePath;
    private String name;
    private int similarity = 70;
    private int colorTolerance = 30;
    private boolean ignoreLevelBadge = false;
    private int width = 0;
    private int height = 0;

    // نظام العلامة المميزة المرجعية (Landmark Anchor)
    private boolean hasAnchor = false;
    private String anchorImagePath = null;
    private int anchorRelativeX = 0;
    private int anchorRelativeY = 0;
    private int anchorSimilarity = 75;
    private int anchorTolerance = 35;

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

    public boolean hasAnchor() { return hasAnchor; }
    public void setHasAnchor(boolean hasAnchor) { this.hasAnchor = hasAnchor; }

    public String getAnchorImagePath() { return anchorImagePath; }
    public void setAnchorImagePath(String anchorImagePath) { this.anchorImagePath = anchorImagePath; }

    public int getAnchorRelativeX() { return anchorRelativeX; }
    public void setAnchorRelativeX(int anchorRelativeX) { this.anchorRelativeX = anchorRelativeX; }

    public int getAnchorRelativeY() { return anchorRelativeY; }
    public void setAnchorRelativeY(int anchorRelativeY) { this.anchorRelativeY = anchorRelativeY; }

    public int getAnchorSimilarity() { return anchorSimilarity; }
    public void setAnchorSimilarity(int anchorSimilarity) { this.anchorSimilarity = anchorSimilarity; }

    public int getAnchorTolerance() { return anchorTolerance; }
    public void setAnchorTolerance(int anchorTolerance) { this.anchorTolerance = anchorTolerance; }

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
            item.hasAnchor = this.hasAnchor;
            item.anchorImagePath = this.anchorImagePath;
            item.anchorRelativeX = this.anchorRelativeX;
            item.anchorRelativeY = this.anchorRelativeY;
            item.anchorSimilarity = this.anchorSimilarity;
            item.anchorTolerance = this.anchorTolerance;
            return item;
        }
    }
}
