package com.example.myautomationapp;

public class Macro {
    private String name;
    private String orientation;
    private String iconName;
    private boolean isConfigured;

    public Macro(String name, String orientation, String iconName) {
        this.name = name;
        this.orientation = orientation;
        this.iconName = iconName;
        this.isConfigured = false; // جديد دائماً
    }

    public String getName() { return name; }
    public String getOrientation() { return orientation; }
    public String getIconName() { return iconName; }
    public boolean isConfigured() { return isConfigured; }
    public void setConfigured(boolean configured) { isConfigured = configured; }
}
