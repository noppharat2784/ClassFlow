package th.ac.vu.classflow.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.ProjectProgressWeekItem;

public final class ProjectProgressWeekAdapter extends RecyclerView.Adapter<ProjectProgressWeekAdapter.ViewHolder> {

    public interface OnWeekSelectedListener {
        void onWeekSelected(ProjectProgressWeekItem item);
    }

    private final List<ProjectProgressWeekItem> items = new ArrayList<>();
    private final OnWeekSelectedListener listener;
    private int selectedWeekNumber = 1;

    public ProjectProgressWeekAdapter(OnWeekSelectedListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ProjectProgressWeekItem> values, int selectedWeekNumber) {
        this.selectedWeekNumber = selectedWeekNumber;
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        if (values != null) items.addAll(values);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    public void setSelectedWeekNumber(int weekNumber) {
        if (this.selectedWeekNumber != weekNumber) {
            this.selectedWeekNumber = weekNumber;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project_progress_week, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), selectedWeekNumber, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView titleView;
        private final TextView currentBadge;
        private final TextView typeView;
        private final Chip statusChip;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.week_card);
            titleView = itemView.findViewById(R.id.week_title);
            currentBadge = itemView.findViewById(R.id.current_week_badge);
            typeView = itemView.findViewById(R.id.week_type);
            statusChip = itemView.findViewById(R.id.week_status_chip);
        }

        void bind(ProjectProgressWeekItem item, int selectedWeek, OnWeekSelectedListener listener) {
            Context context = itemView.getContext();

            titleView.setText(String.format(Locale.US, "Week %d — %s", item.getWeekNumber(), item.getWeekTitle()));

            if (item.isCurrentWeek()) {
                currentBadge.setVisibility(View.VISIBLE);
            } else {
                currentBadge.setVisibility(View.GONE);
            }

            typeView.setText(context.getString(R.string.progress_type_format, item.getProgressType()));

            if (item.getWeekNumber() == selectedWeek) {
                cardView.setStrokeWidth(Math.round(2 * context.getResources().getDisplayMetrics().density));
            } else {
                cardView.setStrokeWidth(0);
            }

            ProjectProgress progress = item.getProgress();
            String status = progress != null ? progress.getOverallStatus() : ProgressPresentation.NOT_RECORDED;
            statusChip.setText(ProgressPresentation.label(status));
            bindStatusChipColor(statusChip, status);

            itemView.setOnClickListener(v -> listener.onWeekSelected(item));
        }

        private static void bindStatusChipColor(Chip chip, String status) {
            Context context = chip.getContext();
            int containerColorRes;
            int textColorRes;

            if (Project.STATUS_ON_TRACK.equals(status)) {
                containerColorRes = R.color.status_active_container;
                textColorRes = R.color.status_on_track;
            } else if (Project.STATUS_NEEDS_ATTENTION.equals(status)) {
                containerColorRes = R.color.status_attention_container;
                textColorRes = R.color.status_needs_attention;
            } else if (Project.STATUS_BLOCKED.equals(status)) {
                containerColorRes = R.color.status_blocked_container;
                textColorRes = R.color.status_blocked;
            } else if (Project.STATUS_COMPLETED.equals(status)) {
                containerColorRes = R.color.status_completed_container;
                textColorRes = R.color.status_completed;
            } else {
                containerColorRes = R.color.status_planned_container;
                textColorRes = R.color.status_not_started;
            }

            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, containerColorRes)));
            chip.setTextColor(ContextCompat.getColor(context, textColorRes));
        }
    }
}
