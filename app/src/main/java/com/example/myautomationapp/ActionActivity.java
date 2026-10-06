package com.example.myautomationapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ActionActivity extends Activity {
    private List<Action> actionList = new ArrayList<>();
    private ActionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);

        RecyclerView recyclerView = findViewById(R.id.recyclerActions);
        Button btnAddAction = findViewById(R.id.btnAddAction);
        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnPlay = findViewById(R.id.btnPlay);

        adapter = new ActionAdapter(actionList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // زر الرجوع (تصغير الشاشة)
        btnBack.setOnClickListener(v -> finish());

        // زر التشغيل (مثلث)
        btnPlay.setOnClickListener(v -> {
            if (actionList.isEmpty()) {
                Toast.makeText(this, "لا يوجد أكشنات لتشغيلها!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "جاري تشغيل " + actionList.size() + " أكشن...", Toast.LENGTH_SHORT).show();
                // هنا منطق التشغيل الفعلي لاحقاً
            }
        });

        // زر إضافة أكشن
        btnAddAction.setOnClickListener(v -> showAddActionDialog());
    }

    private void showAddActionDialog() {
        String[] actions = {
                "Click Image (النقر على صورة)",
                "Click (x, y) (النقر على إحداثيات)",
                "Click Text (النقر على نص)",
                "Swipe (x, y) (السحب)",
                "Wait (الانتظار)",
                "Open App (فتح تطبيق)",
                "Press Back (زر الرجوع)",
                "Press Home (الزر الرئيسي)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("اختر الأكشن");
        builder.setItems(actions, (dialog, which) -> {
            String selected = actions[which];
            // إضافة أكشن وهمي للمعاينة (لاحقاً رح نضيف الشاشات الخاصة بكل أكشن)
            actionList.add(new Action(selected.split(" \\(")[0], "جاري الإعداد..."));
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت الإضافة: " + selected, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("إلغاء", null);
        builder.show();
    }
}
