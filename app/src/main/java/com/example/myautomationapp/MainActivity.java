package com.example.myautomationapp;

import android.app.Activity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        // قائمة تجريبية للمعاينة
        List<String> macroList = new ArrayList<>();
        macroList.add("تجريبي قتل");
        macroList.add("بس لقطة فتح الحقيبة");
        macroList.add("حدث النداء قلعة الهيبة");
        macroList.add("للنداء، هيبة");

        MacroAdapter adapter = new MacroAdapter(macroList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            // هنا رح نضيف كود التقاط الشاشة لاحقاً
        });
    }
}
