package com.example.myautomationapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

            if (!AutoAccessibilityService.isRunning()) {
                Toast.makeText(this, "يرجى تفعيل خدمة Auto في إمكانية الوصول أولاً!", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }

            Toast.makeText(this, "جاري تنفيذ الأكشنات تلقائياً...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                for (Action action : GlobalData.actionList) {
                    if (AutoAccessibilityService.instance != null) {
                        if ("Click (x, y)".equals(action.getType())) {
                            AutoAccessibilityService.instance.click(action.getX(), action.getY());
                        } else if ("Press Back".equals(action.getType())) {
                            AutoAccessibilityService.instance.pressBack();
                        } else if ("Swipe".equals(action.getType())) {
                            // سحب تجريبي للأعلى
                            AutoAccessibilityService.instance.swipe(500, 1200, 500, 400, 400);
                        }
                    }

                    try {
                        Thread.sleep(action.getDelayMs());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(ActionActivity.this, "اكتمل تنفيذ الأكشنات بنجاح!", Toast.LENGTH_SHORT).show()
                );
            }).start();
        });

        btnAddAction.setOnClickListener(v -> showActionSelectionDialog());

        // استقبال الإحداثيات
        coordReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int x = intent.getIntExtra("x", 0);
                int y = intent.getIntExtra("y", 0);
                GlobalData.actionList.add(new Action("Click (x, y)", "نقر عند X: " + x + " , Y: " + y, x, y));
                adapter.notifyDataSetChanged();
                Toast.makeText(ActionActivity.this, "تمت إضافة النقر: (" + x + ", " + y + ")", Toast.LENGTH_SHORT).show();
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

        // 1. خيار النقر عند إحداثيات
        dialogView.findViewById(R.id.btnClickXY).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(ActionActivity.this, CoordinatePickerService.class);
            startService(intent);
        });

        // 2. خيار النقر على صورة (Macrorify Vision)
        dialogView.findViewById(R.id.btnClickImage).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "ميزة النقر البصري (سنبنيها في الخطوة التالية)", Toast.LENGTH_SHORT).show();
        });

        // 3. خيار السحب (Swipe)
        dialogView.findViewById(R.id.btnSwipe).setOnClickListener(v -> {
            dialog.dismiss();
            Action swipeAction = new Action("Swipe", "سحب للأعلى من (500,1200) إلى (500,400)");
            swipeAction.setDelayMs(800);
            GlobalData.actionList.add(swipeAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة أمر السحب", Toast.LENGTH_SHORT).show();
        });

        // 4. خيار الانتظار (Wait)
        dialogView.findViewById(R.id.btnWait).setOnClickListener(v -> {
            dialog.dismiss();
            showWaitDurationDialog();
        });

        // 5. خيار الرجوع (Press Back)
        dialogView.findViewById(R.id.btnPressBack).setOnClickListener(v -> {
            dialog.dismiss();
            Action backAction = new Action("Press Back", "الضغط على زر رجوع الجهاز");
            backAction.setDelayMs(500);
            GlobalData.actionList.add(backAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة أمر الرجوع", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnOpenApp).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Open App (قريباً)", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showWaitDurationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("مدة الانتظار (بالثواني)");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText("1");
        builder.setView(input);

        builder.setPositiveButton("إضافة", (d, which) -> {
            String text = input.getText().toString();
            int seconds = text.isEmpty() ? 1 : Integer.parseInt(text);
            Action waitAction = new Action("Wait", "انتظار " + seconds + " ثواني");
            waitAction.setDelayMs(seconds * 1000);
            GlobalData.actionList.add(waitAction);
            adapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("إلغاء", null);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (coordReceiver != null) {
            unregisterReceiver(coordReceiver);
        }
    }
}
