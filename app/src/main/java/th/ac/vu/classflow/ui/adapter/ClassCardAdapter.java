package th.ac.vu.classflow.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.ui.model.DashboardClassSummary;

public final class ClassCardAdapter extends RecyclerView.Adapter<ClassCardAdapter.ViewHolder> {

    public interface OnOpenClassListener {
        void onOpenClass(String classId);
    }

    private final List<DashboardClassSummary> items = new ArrayList<>();
    private final OnOpenClassListener listener;

    public ClassCardAdapter(OnOpenClassListener listener) {
        this.listener = listener;
    }

    public void submitList(List<DashboardClassSummary> newItems) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize);
        }
        if (newItems != null) {
            items.addAll(newItems);
        }
        if (!items.isEmpty()) {
            notifyItemRangeInserted(0, items.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DashboardClassSummary item = items.get(position);
        Context context = holder.itemView.getContext();

        holder.className.setText(item.getClassOffering().getName());
        holder.track.setText(String.format(Locale.getDefault(), "%s · %s",
                item.getTrack().getCode(), item.getTrack().getName()));
        holder.currentWeek.setText(String.format(Locale.getDefault(), "Week %d — %s",
                item.getCurrentWeek().getWeekNumber(), item.getCurrentWeek().getTitle()));

        // Active Enrolled count
        holder.enrolledChip.setText(context.getString(R.string.class_enrolled_count, item.getActiveEnrollmentCount()));

        // Blocked count
        if (item.getBlockedCount() > 0) {
            holder.blockedChip.setVisibility(View.VISIBLE);
            holder.blockedChip.setText(context.getString(R.string.class_blocked_count, item.getBlockedCount()));
        } else {
            holder.blockedChip.setVisibility(View.GONE);
        }

        // Needs attention count
        if (item.getNeedsAttentionCount() > 0) {
            holder.attentionChip.setVisibility(View.VISIBLE);
            holder.attentionChip.setText(context.getString(R.string.class_attention_count, item.getNeedsAttentionCount()));
        } else {
            holder.attentionChip.setVisibility(View.GONE);
        }

        // Partial warning
        holder.partialWarning.setVisibility(item.isPartiallyUnavailable() ? View.VISIBLE : View.GONE);

        holder.open.setOnClickListener(view ->
                listener.onOpenClass(item.getClassOffering().getClassId()));
        holder.itemView.setOnClickListener(view ->
                listener.onOpenClass(item.getClassOffering().getClassId()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView className;
        private final TextView track;
        private final TextView currentWeek;
        private final Chip enrolledChip;
        private final Chip blockedChip;
        private final Chip attentionChip;
        private final TextView partialWarning;
        private final MaterialButton open;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            className = itemView.findViewById(R.id.class_card_name);
            track = itemView.findViewById(R.id.class_card_track);
            currentWeek = itemView.findViewById(R.id.class_card_current_week);
            enrolledChip = itemView.findViewById(R.id.class_card_enrolled_chip);
            blockedChip = itemView.findViewById(R.id.class_card_blocked_chip);
            attentionChip = itemView.findViewById(R.id.class_card_attention_chip);
            partialWarning = itemView.findViewById(R.id.class_card_partial_warning);
            open = itemView.findViewById(R.id.class_card_open);
        }
    }
}
