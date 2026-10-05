package com.example.myautomationapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private List<Macro> macroList = new ArrayList<>();
    private MacroAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        adapter = new MacroAdapter(macroList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showNewMacroDialog());
    }

    private void showNewMacroDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("New Macro");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Name");
        input.setTextColor(getResources().getColor(android.R.color.white));
        builder.setView(input);

        builder.setPositiveButton("NEXT", (dialog, which) -> {
            String name = input.getText().toString();
            if (!name.isEmpty()) {
                showOrientationDialog(name);
            } else {
                Toast.makeText(MainActivity.this, "Please enter a name", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showOrientationDialog(String name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("Orientation");

        final RadioGroup radioGroup = new RadioGroup(this);
        RadioButton portrait = new RadioButton(this);
        portrait.setText("Portrait");
        portrait.setTextColor(getResources().getColor(android.R.color.white));
        portrait.setId(1);
        RadioButton landscape = new RadioButton(this);
        landscape.setText("Landscape");
        landscape.setTextColor(getResources().getColor(android.R.color.white));
        landscape.setId(2);

        radioGroup.addView(portrait);
        radioGroup.addView(landscape);
        radioGroup.check(1); // Default to Portrait

        builder.setView(radioGroup);
        builder.setPositiveButton("NEXT", (dialog, which) -> {
            String orientation = (radioGroup.getCheckedRadioButtonId() == 1) ? "Portrait" : "Landscape";
            showIconDialog(name, orientation);
        });
        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showIconDialog(String name, String orientation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        builder.setTitle("Icon");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Game");
        input.setTextColor(getResources().getColor(android.R.color.white));
        builder.setView(input);

        builder.setPositiveButton("DONE", (dialog, which) -> {
            String iconName = input.getText().toString();
            if (iconName.isEmpty()) iconName = "Default";
            macroList.add(new Macro(name, orientation, iconName));
            adapter.notifyDataSetChanged();
            Toast.makeText(MainActivity.this, "Macro Created!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
