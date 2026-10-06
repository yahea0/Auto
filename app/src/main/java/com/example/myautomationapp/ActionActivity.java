package com.example.myautomationapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ActionActivity extends AppCompatActivity {
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

        adapter = new ActionAdapter(GlobalData.actionList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        // زر التشغيل الحقيقي
        btnPlay.setOnClickListener(v -> {
            if (GlobalData.actionList.isEmpty()) {
                Toast.makeText(this, "لا يوجد أكشنات مضافة بعد!", Toast.LENGTH_SHORT).show();
                return;
            }

            // فحص هل خدمة إمكانية الوصول مفعّلة
            if (!AutoAccessibilityService.isRunning()) {
                Toast.makeText(this, "يجب تفعيل خدمة إمكانية الوصول للتطبيق للبدء بالنقر!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                return;
            }

            Toast.makeText(this, "جاري تنفيذ الأكشنات تلقائياً...", Toast.LENGTH_SHORT).show();

            // تنفيذ الأكشنات بالترتيب في مسار خلفي لتجنب تجميد الشاشة
            new Thread(() -> {
                for (Action action : GlobalData.actionList) {
                    if ("Click (x, y)".equals(action.getType()) && AutoAccessibilityService.instance != null) {
                        AutoAccessibilityService.instance.click(action.getX(), action.getY());
                    }
                    try {
                        Thread.sleep(action.getDelayMs());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(ActionActivity.this, "اكتمل تنفيذ جميع الأكشنات بنجاح!", Toast.LENGTH_SHORT).show()
                );
            }).start();
        });

        btnAddAction.setOnClickListener(v -> showActionSelectionDialog());

        // استقبال الإحداثيات بأمان وتوافق مع كافة أنظمة أندرويد
        coordReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int x = intent.getIntExtra("x", 0);
                int y = intent.getIntExtra("y", 0);
                GlobalData.actionList.add(new Action("Click (x, y)", "X: " + x + ", Y: " + y, x, y));
                adapter.notifyDataSetChanged();
                Toast.makeText(ActionActivity.this, "تمت إضافة النقر عند: (" + x + ", " + y + ")", Toast.LENGTH_SHORT).show();
            }
        };

        ContextCompat.registerReceiver(
                this,
                coordReceiver,
                new IntentFilter("COORDINATES_PICKED"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private void showActionSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_action_select, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.btnClickXY).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(ActionActivity.this, CoordinatePickerService.class);
            startService(intent);
        });

        dialogView.findViewById(R.id.btnClickImage).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Click Image (جاري بناؤه في المرحلة القادمة)", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnSwipe).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Swipe (جاري بناؤه في المرحلة القادمة)", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnWait).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Wait (جاري بناؤه في المرحلة القادمة)", Toast.LENGTH_SHORT).show();
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
