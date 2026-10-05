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
    private List<Macro> macroList;

    public MacroAdapter(List<Macro> macroList) {
        this.macroList = macroList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_macro, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Macro macro = macroList.get(position);
        holder.tvName.setText(macro.getName());
        holder.tvDetails.setText(macro.getOrientation() + " | " + macro.getIconName());

        holder.btnEdit.setOnClickListener(v -> {
            Toast.makeText(v.getContext(), "وضع التحرير: " + macro.getName(), Toast.LENGTH_SHORT).show();
        });

        holder.btnPlay.setOnClickListener(v -> {
            Toast.makeText(v.getContext(), "تشغيل: " + macro.getName(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return macroList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDetails;
        Button btnEdit, btnPlay;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvDetails = itemView.findViewById(R.id.tvDetails);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnPlay = itemView.findViewById(R.id.btnPlay);
        }
    }
}
