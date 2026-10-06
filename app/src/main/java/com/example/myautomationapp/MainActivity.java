package com.example.myautomationapp;

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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
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

        // طلب صلاحية الإشعارات لأندرويد 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQ_CODE);
            }
        }
    }

    public void checkOverlayPermission(Macro macro) {
        pendingMacro = macro;

        // أولاً: التأكد من صلاحية الظهور فوق التطبيقات
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            return;
        }

        // ثانياً: التأكد من خدمة إمكانية الوصول (محرك النقر)
        if (!AutoAccessibilityService.isRunning()) {
            Toast.makeText(this, "يرجى تفعيل خدمة Auto في إمكانية الوصول للتمكن من النقر", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        // ثالثاً: طلب صلاحية التقاط الشاشة
        requestMediaProjection();
    }

    private void requestMediaProjection() {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), MEDIA_PROJECTION_REQ_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                if (pendingMacro != null) {
                    checkOverlayPermission(pendingMacro);
                }
            } else {
                Toast.makeText(this, "صلاحية الظهور فوق التطبيقات مطلوبة!", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == MEDIA_PROJECTION_REQ_CODE) {
            if (resultCode == RESULT_OK && data != null && pendingMacro != null) {
                Intent serviceIntent = new Intent(MainActivity.this, FloatingWindowService.class);
                serviceIntent.putExtra("macro_name", pendingMacro.getName());
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "جاري تشغيل النافذة العائمة...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "يجب الموافقة على التقاط الشاشة لتشغيل الأتمتة", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showNewMacroDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Orientation");
        final String[] orientations = {"Portrait", "Landscape"};
        builder.setItems(orientations, (dialog, which) -> showIconDialog(name, orientations[which]));
        builder.setNegativeButton("CANCEL", null);
        builder.show();
    }

    private void showIconDialog(String name, String orientation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
