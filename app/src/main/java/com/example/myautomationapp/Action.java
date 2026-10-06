package com.example.myautomationapp;

public class Action {
    private String type;     // مثل: "Click Image", "Wait", "Swipe"
    private String detail;   // تفاصيل الأكشن

    public Action(String type, String detail) {
        this.type = type;
        this.detail = detail;
    }

    public String getType() { return type; }
    public String getDetail() { return detail; }
}
