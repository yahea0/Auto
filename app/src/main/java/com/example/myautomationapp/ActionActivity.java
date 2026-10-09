package com.example.myautomationapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
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

        // زر التشغيل الذكي مع فحص الشروط والرؤية بالملي
        btnPlay.setOnClickListener(v -> {
            if (GlobalData.actionList.isEmpty()) {
                Toast.makeText(this, "لا يوجد أكشنات مضافة بعد!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!AutoAccessibilityService.isRunning()) {
                Toast.makeText(this, "يرجى تفعيل خدمة إمكانية الوصول أولاً!", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }

            Toast.makeText(this, "جاري تنفيذ الأكشنات مع الفحص الصارم للشروط...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                for (Action action : GlobalData.actionList) {
                    if (action.isDisabled()) continue;

                    if (action.getDelayBeforeMs() > 0) {
                        try { Thread.sleep(action.getDelayBeforeMs()); } catch (InterruptedException ignored) {}
                    }

                    boolean shouldExecute = true;

                    // التحقق الصارم من شرط الصورة إن وجد
                    if (action.hasCondition() && "Image Appear".equals(action.getConditionType())) {
                        shouldExecute = false;
                        Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                        if (screen != null && action.getImagePath() != null) {
                            Bitmap template = BitmapFactory.decodeFile(action.getImagePath());
                            if (template != null) {
                                Point match = VisionEngine.findActionTarget(screen, template, action);
                                boolean found = (match != null);
                                shouldExecute = action.isNotAppear() ? !found : found;
                                template.recycle();
                            }
                            screen.recycle();
                        }
                    }

                    if (shouldExecute && AutoAccessibilityService.instance != null) {
                        String type = action.getType();
                        if ("Click (x, y)".equals(type)) {
                            AutoAccessibilityService.instance.click(action.getX() + action.getOffsetX(), action.getY() + action.getOffsetY());
                        } else if ("Click Image".equals(type) || action.getImagePath() != null) {
                            Bitmap screen = ScreenCaptureManager.getInstance().captureScreen();
                            if (screen != null && action.getImagePath() != null) {
                                Bitmap template = BitmapFactory.decodeFile(action.getImagePath());
                                if (template != null) {
                                    Point match = VisionEngine.findActionTarget(screen, template, action);
                                    if (match != null) {
                                        // احتساب مركز الهدف اللحظي داخل Custom Region أو Full Screen بدقة تامة
                                        int targetX = match.x + (template.getWidth() / 2) + action.getOffsetX();
                                        int targetY = match.y + (template.getHeight() / 2) + action.getOffsetY();
                                        AutoAccessibilityService.instance.click(targetX, targetY);
                                    }
                                    template.recycle();
                                }
                                screen.recycle();
                            }
                        } else if ("Swipe".equals(type)) {
                            AutoAccessibilityService.instance.swipe(500, 1200, 500, 400, 400);
                        } else if ("Press Back".equals(type)) {
                            AutoAccessibilityService.instance.pressBack();
                        } else if ("Press Home".equals(type)) {
                            AutoAccessibilityService.instance.pressHome();
                        } else if ("Open Recent".equals(type)) {
                            AutoAccessibilityService.instance.openRecents();
                        } else if ("Notification".equals(type)) {
                            AutoAccessibilityService.instance.openNotifications();
                        } else if ("Screenshot".equals(type)) {
                            AutoAccessibilityService.instance.takeScreenshot();
                        }
                    }

                    try {
                        Thread.sleep(action.getDelayAfterMs() > 0 ? action.getDelayAfterMs() : 500);
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

        // 1. Click XY
        dialogView.findViewById(R.id.btnClickXY).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(ActionActivity.this, CoordinatePickerService.class);
            startService(intent);
        });

        // 2. Click Image
        dialogView.findViewById(R.id.btnClickImage).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(ActionActivity.this, ImageCropPickerService.class);
            intent.putExtra("is_click_image", true);
            startService(intent);
        });

        // 3. Click Text
        dialogView.findViewById(R.id.btnClickText).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Click Text (قريباً)", Toast.LENGTH_SHORT).show();
        });

        // 4. Swipe
        dialogView.findViewById(R.id.btnSwipe).setOnClickListener(v -> {
            dialog.dismiss();
            Action swipeAction = new Action("Swipe", "سحب للأعلى (500,1200) إلى (500,400)");
            swipeAction.setDelayMs(600);
            GlobalData.actionList.add(swipeAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة أمر السحب", Toast.LENGTH_SHORT).show();
        });

        // 5. Press Back
        dialogView.findViewById(R.id.btnPressBack).setOnClickListener(v -> {
            dialog.dismiss();
            Action backAction = new Action("Press Back", "زر رجوع الجهاز");
            backAction.setDelayMs(500);
            GlobalData.actionList.add(backAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة أمر الرجوع", Toast.LENGTH_SHORT).show();
        });

        // 6. Press Home
        dialogView.findViewById(R.id.btnPressHome).setOnClickListener(v -> {
            dialog.dismiss();
            Action homeAction = new Action("Press Home", "زر الشاشة الرئيسية");
            homeAction.setDelayMs(500);
            GlobalData.actionList.add(homeAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة أمر الهوم", Toast.LENGTH_SHORT).show();
        });

        // 7. Open Recent
        dialogView.findViewById(R.id.btnOpenRecent).setOnClickListener(v -> {
            dialog.dismiss();
            Action recentAction = new Action("Open Recent", "شاشة التطبيقات الحديثة");
            recentAction.setDelayMs(500);
            GlobalData.actionList.add(recentAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة أمر التطبيقات الحديثة", Toast.LENGTH_SHORT).show();
        });

        // 8. Notification
        dialogView.findViewById(R.id.btnOpenNotification).setOnClickListener(v -> {
            dialog.dismiss();
            Action notifAction = new Action("Notification", "فتح لوحة الإشعارات");
            notifAction.setDelayMs(500);
            GlobalData.actionList.add(notifAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة أمر الإشعارات", Toast.LENGTH_SHORT).show();
        });

        // 9. Screenshot
        dialogView.findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            dialog.dismiss();
            Action shotAction = new Action("Screenshot", "أخذ لقطة شاشة");
            shotAction.setDelayMs(600);
            GlobalData.actionList.add(shotAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة لقطة الشاشة", Toast.LENGTH_SHORT).show();
        });

        // 10. Wait
        dialogView.findViewById(R.id.btnWait).setOnClickListener(v -> {
            dialog.dismiss();
            Action waitAction = new Action("Wait", "انتظار 1 ثانية");
            waitAction.setDelayMs(1000);
            GlobalData.actionList.add(waitAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة الانتظار", Toast.LENGTH_SHORT).show();
        });

        // 11. Stop Macro
        dialogView.findViewById(R.id.btnStopMacro).setOnClickListener(v -> {
            dialog.dismiss();
            Action stopAction = new Action("Stop Macro", "إيقاف الماكرو");
            GlobalData.actionList.add(stopAction);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "تمت إضافة أمر الإيقاف", Toast.LENGTH_SHORT).show();
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
