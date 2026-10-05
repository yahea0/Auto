package com.example.myautomationapp;

public class Macro {
    private String name;
    private String orientation;
    private String iconName;

    public Macro(String name, String orientation, String iconName) {
        this.name = name;
        this.orientation = orientation;
        this.iconName = iconName;
    }

    public String getName() { return name; }
    public String getOrientation() { return orientation; }
    public String getIconName() { return iconName; }
}
