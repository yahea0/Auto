package com.example.myautomationapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ActionActivity extends Activity {
    private ActionAdapter adapter;
    private BroadcastReceiver coordReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);

        RecyclerView recyclerView = findViewById(R.id.recyclerActions);
        Button btnAddAction = findViewById(R.id.btnAddAction);
        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnPlay = findViewById(R.id.btnPlay);

        // تحميل الأكشنات المحفوظة من GlobalData
        adapter = new ActionAdapter(GlobalData.actionList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // زر الرجوع (تصغير الشاشة)
        btnBack.setOnClickListener(v -> finish()); // ملاحظة: الحفظ صار تلقائي في GlobalData

        // زر التشغيل
        btnPlay.setOnClickListener(v -> {
            if (GlobalData.actionList.isEmpty()) {
                Toast.makeText(this, "لا يوجد أكشنات!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "جاري تشغيل " + GlobalData.actionList.size() + " أكشن...", Toast.LENGTH_SHORT).show();
            }
        });

        // زر إضافة أكشن
        btnAddAction.setOnClickListener(v -> showActionSelectionDialog());

        // مستقبل الإحداثيات من خدمة التقاط الإحداثيات
        coordReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int x = intent.getIntExtra("x", 0);
                int y = intent.getIntExtra("y", 0);
                GlobalData.actionList.add(new Action("Click (x, y)", "X: " + x + ", Y: " + y));
                adapter.notifyDataSetChanged();
                Toast.makeText(ActionActivity.this, "تمت إضافة النقر عند: " + x + ", " + y, Toast.LENGTH_SHORT).show();
            }
        };
        registerReceiver(coordReceiver, new IntentFilter("COORDINATES_PICKED"), Context.RECEIVER_EXPORTED);
    }

    private void showActionSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_action_select, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.btnClickXY).setOnClickListener(v -> {
            dialog.dismiss();
            // إطلاق خدمة التقاط الإحداثيات
            Intent intent = new Intent(ActionActivity.this, CoordinatePickerService.class);
            startService(intent);
        });

        dialogView.findViewById(R.id.btnClickImage).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Click Image (قريباً)", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnSwipe).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Swipe (قريباً)", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnWait).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Wait (قريباً)", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnOpenApp).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Open App (قريباً)", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnPressBack).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Press Back (قريباً)", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (coordReceiver != null) {
            unregisterReceiver(coordReceiver);
        }
    }
}
