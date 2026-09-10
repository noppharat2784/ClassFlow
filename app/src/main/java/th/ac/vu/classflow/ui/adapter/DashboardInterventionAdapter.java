package th.ac.vu.classflow.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.ui.model.DashboardItem;

public final class DashboardInterventionAdapter extends RecyclerView.Adapter<DashboardInterventionAdapter.ViewHolder> {

    public enum Filter {
        ALL,
        STUDENTS,
        PROJECTS
    }

    public interface OnInterventionClickListener {
        void onStudentClicked(@NonNull String studentId, @NonNull String classId, @NonNull String weekId);
        void onProjectClicked(@NonNull String projectId);
    }

    private final List<DashboardItem> allItems = new ArrayList<>();
    private final List<DashboardItem> visibleItems = new ArrayList<>();
    private final OnInterventionClickListener listener;
    private Filter currentFilter = Filter.ALL;

    public DashboardInterventionAdapter(@NonNull OnInterventionClickListener listener) {
        this.listener = listener;
    }

    public void submitList(@Nullable List<DashboardItem> items) {
        allItems.clear();
        if (items != null) {
            allItems.addAll(items);
        }
        applyFilter();
    }

    public void setFilter(@NonNull Filter filter) {
        if (this.currentFilter != filter) {
            this.currentFilter = filter;
            applyFilter();
        }
    }

    public int getAllCount() {
        return allItems.size();
    }

    public int getStudentCount() {
        int count = 0;
        for (DashboardItem item : allItems) {
            if (item.getType() == DashboardItem.Type.STUDENT) count++;
        }
        return count;
    }

    public int getProjectCount() {
        int count = 0;
        for (DashboardItem item : allItems) {
            if (item.getType() == DashboardItem.Type.PROJECT) count++;
        }
        return count;
    }

    private void applyFilter() {
        int oldSize = visibleItems.size();
        visibleItems.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);

        for (DashboardItem item : allItems) {
            if (currentFilter == Filter.ALL) {
                visibleItems.add(item);
            } else if (currentFilter == Filter.STUDENTS && item.getType() == DashboardItem.Type.STUDENT) {
                visibleItems.add(item);
            } else if (currentFilter == Filter.PROJECTS && item.getType() == DashboardItem.Type.PROJECT) {
                visibleItems.add(item);
            }
        }

        if (!visibleItems.isEmpty()) {
            notifyItemRangeInserted(0, visibleItems.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dashboard_intervention_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(visibleItems.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardView;
        private final Chip typeChip;
        private final Chip statusChip;
        private final TextView entityNameView;
        private final TextView contextView;
        private final LinearLayout blockerBox;
        private final TextView blockerTextView;
        private final TextView noteView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            typeChip = itemView.findViewById(R.id.intervention_type_chip);
            statusChip = itemView.findViewById(R.id.intervention_status_chip);
            entityNameView = itemView.findViewById(R.id.intervention_entity_name);
            contextView = itemView.findViewById(R.id.intervention_context);
            blockerBox = itemView.findViewById(R.id.intervention_blocker_box);
            blockerTextView = itemView.findViewById(R.id.intervention_blocker_text);
            noteView = itemView.findViewById(R.id.intervention_note);
        }

        void bind(DashboardItem item, OnInterventionClickListener listener) {
            Context context = itemView.getContext();

            // 1. Type Chip
            if (item.getType() == DashboardItem.Type.STUDENT) {
                typeChip.setText(R.string.type_student);
                typeChip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.soft_blue)));
                typeChip.setTextColor(ContextCompat.getColor(context, R.color.indigo_primary));
                typeChip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.soft_blue)));
            } else {
                typeChip.setText(R.string.type_project);
                typeChip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.indigo_primary_dark)));
                typeChip.setTextColor(ContextCompat.getColor(context, R.color.white));
                typeChip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.indigo_primary_dark)));
            }

            // 2. Status Chip
            boolean isBlocked = StudentProgress.STATUS_BLOCKED.equals(item.getStatus());
            if (isBlocked) {
                statusChip.setText(R.string.status_blocked_badge);
                statusChip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_blocked_container)));
                statusChip.setTextColor(ContextCompat.getColor(context, R.color.status_blocked));
                statusChip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_blocked)));
            } else {
                statusChip.setText(R.string.status_attention_badge);
                statusChip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_attention_container)));
                statusChip.setTextColor(ContextCompat.getColor(context, R.color.status_needs_attention));
                statusChip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_needs_attention)));
            }

            // 3. Entity Name
            if (item.getType() == DashboardItem.Type.STUDENT) {
                if (item.getStudentNickname() != null && !item.getStudentNickname().trim().isEmpty()) {
                    entityNameView.setText(context.getString(R.string.intervention_student_format,
                            item.getStudentName(), item.getStudentNickname().trim()));
                } else {
                    entityNameView.setText(item.getStudentName());
                }
            } else {
                if (item.getTeamName() != null && !item.getTeamName().trim().isEmpty()) {
                    entityNameView.setText(context.getString(R.string.intervention_student_format,
                            item.getProjectTitle(), item.getTeamName().trim()));
                } else {
                    entityNameView.setText(item.getProjectTitle());
                }
            }

            // 4. Context Row
            contextView.setText(context.getString(R.string.intervention_context_format,
                    item.getClassName(), item.getTrackCode(), item.getWeekNumber()));

            // 5. Blocker Box
            if (isBlocked) {
                blockerBox.setVisibility(View.VISIBLE);
                if (item.getBlocker() != null && !item.getBlocker().trim().isEmpty()) {
                    blockerTextView.setText(context.getString(R.string.intervention_blocker_format, item.getBlocker().trim()));
                } else {
                    blockerTextView.setText(R.string.blocker_details_unavailable);
                }
            } else {
                blockerBox.setVisibility(View.GONE);
            }

            // 6. Teacher Note
            if (item.getType() == DashboardItem.Type.STUDENT
                    && item.getTeacherNote() != null
                    && !item.getTeacherNote().trim().isEmpty()) {
                noteView.setVisibility(View.VISIBLE);
                noteView.setText(context.getString(R.string.intervention_note_format, item.getTeacherNote().trim()));
            } else {
                noteView.setVisibility(View.GONE);
            }

            // 7. Click Navigation
            cardView.setOnClickListener(v -> {
                if (listener == null) return;
                if (item.getType() == DashboardItem.Type.STUDENT) {
                    if (item.getStudentId() != null) {
                        listener.onStudentClicked(item.getStudentId(), item.getClassId(), item.getWeekId());
                    }
                } else {
                    if (item.getProjectId() != null) {
                        listener.onProjectClicked(item.getProjectId());
                    }
                }
            });
        }
    }
}
