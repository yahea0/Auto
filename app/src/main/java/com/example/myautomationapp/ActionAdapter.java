package com.example.myautomationapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.ViewHolder> {
    private List<Action> actionList;

    public ActionAdapter(List<Action> actionList) {
        this.actionList = actionList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_action, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Action action = actionList.get(position);
        holder.tvType.setText(action.getType());
        holder.tvDetail.setText(action.getDetail());
    }

    @Override
    public int getItemCount() {
        return actionList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvDetail;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvType);
            tvDetail = itemView.findViewById(R.id.tvDetail);
        }
    }
}
