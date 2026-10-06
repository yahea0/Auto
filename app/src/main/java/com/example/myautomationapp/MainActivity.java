package com.example.myautomationapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private List<Macro> macroList = new ArrayList<>();
    private MacroAdapter adapter;
    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private static final int MEDIA_PROJECTION_REQ_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQ_CODE = 1002;
    private Macro pendingMacro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        adapter = new MacroAdapter(macroList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showNewMacroDialog());
        
        // طلب صلاحية الإشعارات (مهمة لأندرويد 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQ_CODE);
            }
        }
    }

    // 1. طلب صلاحية "الظهور فوق التطبيقات"
    public void checkOverlayPermission(Macro macro) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        pendingMacro = macro;
    }

    // 2. طلب صلاحية "التقاط الشاشة"
    private void requestMediaProjection(Macro macro) {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), MEDIA_PROJECTION_REQ_CODE);
        pendingMacro = macro;
    }

    // 3. معالجة النتائج
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            // بعد ما نرجع من إعدادات الظهور فوق التطبيقات، نطلب صلاحية التقاط الشاشة
            if (pendingMacro != null) {
                requestMediaProjection(pendingMacro);
            }
        } 
        else if (requestCode == MEDIA_PROJECTION_REQ_CODE) {
            // إذا وافق المستخدم على تسجيل الشاشة
            if (resultCode == RESULT_OK && data != null && pendingMacro != null) {
                // نحفظ بيانات تسجيل الشاشة عشان نمررها للخدمة لاحقاً
                Intent serviceIntent = new Intent(MainActivity.this, FloatingWindowService.class);
                serviceIntent.putExtra("macro_name", pendingMacro.getName());
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                
                // نفتح النافذة العائمة
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "جاري تشغيل النافذة العائمة...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "يجب الموافقة على تسجيل الشاشة لتشغيل الأتمتة", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // دالة عرض حوار الإعدادات (قوية/عادية)
    private void showSetupDialog(Macro macro) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("Interface");
        String[] options = {"إعدادات قوية (Beta)", "إعدادات عادية"};
        builder.setItems(options, (dialog, which) -> {
            macro.setConfigured(true);
            adapter.notifyDataSetChanged();
            checkOverlayPermission(macro); // نبدأ تسلسل الصلاحيات
        });
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    private void showNewMacroDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("New Macro");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Name");
        builder.setView(input);
        builder.setPositiveButton("NEXT", (dialog, which) -> {
            String name = input.getText().toString();
            if (!name.isEmpty()) showOrientationDialog(name);
        });
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    private void showOrientationDialog(String name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("Orientation");
        final String[] orientations = {"Portrait", "Landscape"};
        builder.setItems(orientations, (dialog, which) -> showIconDialog(name, orientations[which]));
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    private void showIconDialog(String name, String orientation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("Icon (Optional)");
        final EditText input = new EditText(this);
        input.setHint("Game");
        builder.setView(input);
        builder.setPositiveButton("DONE", (dialog, which) -> {
            String icon = input.getText().toString().isEmpty() ? "Default" : input.getText().toString();
            macroList.add(new Macro(name, orientation, icon));
            adapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }
}
