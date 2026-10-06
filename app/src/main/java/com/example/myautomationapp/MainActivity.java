package com.example.myautomationapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
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
    }

    public void checkOverlayPermission(Macro macro) {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            pendingMacro = macro;
        } else {
            requestMediaProjection(macro);
        }
    }

    private void requestMediaProjection(Macro macro) {
        android.media.projection.MediaProjectionManager projectionManager = 
                (android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), MEDIA_PROJECTION_REQ_CODE);
        pendingMacro = macro;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Settings.canDrawOverlays(this)) {
                if (pendingMacro != null) requestMediaProjection(pendingMacro);
            } else {
                Toast.makeText(this, "صلاحية الظهور فوق التطبيقات مطلوبة", Toast.LENGTH_SHORT).show();
            }
        } 
        else if (requestCode == MEDIA_PROJECTION_REQ_CODE) {
            if (resultCode == RESULT_OK && data != null && pendingMacro != null) {
                showSetupDialog(pendingMacro);
            } else {
                Toast.makeText(this, "تم إلغاء تسجيل الشاشة", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showSetupDialog(Macro macro) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("Interface");
        String[] options = {"إعدادات قوية (Beta)", "إعدادات عادية"};
        builder.setItems(options, (dialog, which) -> {
            macro.setConfigured(true);
            adapter.notifyDataSetChanged();
            
            Intent intent = new Intent(MainActivity.this, FloatingWindowService.class);
            intent.putExtra("macro_name", macro.getName());
            
            // نستخدم startForegroundService لأندرويد 8+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            finish();
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
