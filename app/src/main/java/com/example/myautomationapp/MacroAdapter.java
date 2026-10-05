package com.example.myautomationapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MacroAdapter extends RecyclerView.Adapter<MacroAdapter.ViewHolder> {
    private List<String> macroNames;

    public MacroAdapter(List<String> macroNames) {
        this.macroNames = macroNames;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_macro, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String name = macroNames.get(position);
        holder.tvName.setText(name);
        holder.tvVersion.setText("1.0.0");

        holder.btnEdit.setOnClickListener(v -> {
            Toast.makeText(v.getContext(), "وضع التحرير: " + name, Toast.LENGTH_SHORT).show();
        });

        holder.btnPlay.setOnClickListener(v -> {
            Toast.makeText(v.getContext(), "تشغيل: " + name, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return macroNames.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvVersion;
        Button btnEdit, btnPlay;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvVersion = itemView.findViewById(R.id.tvVersion);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnPlay = itemView.findViewById(R.id.btnPlay);
        }
    }
}
