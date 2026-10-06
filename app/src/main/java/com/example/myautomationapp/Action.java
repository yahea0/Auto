package com.example.myautomationapp;

public class Action {
    private String type;     // مثل: "Click (x, y)", "Wait", "Swipe"
    private String detail;   // تفاصيل الأكشن للعرض في القائمة
    private int x;
    private int y;
    private int delayMs = 500; // تأخير زمني افتراضي (نصف ثانية)

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
}
