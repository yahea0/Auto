package com.example.myautomationapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("Auto Automation");
        title.setTextColor(Color.parseColor("#FFD700"));
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        
        Button editModeBtn = new Button(this);
        editModeBtn.setText("EDIT MODE");
        editModeBtn.setBackgroundColor(Color.parseColor("#FFD700"));
        editModeBtn.setTextColor(Color.parseColor("#121212"));
        
        layout.addView(title);
        layout.addView(editModeBtn);
        
        setContentView(layout);
    }
}
