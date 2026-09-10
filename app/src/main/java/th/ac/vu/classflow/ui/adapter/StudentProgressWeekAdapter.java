package th.ac.vu.classflow.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.StudentProgressWeekItem;

public final class StudentProgressWeekAdapter extends RecyclerView.Adapter<StudentProgressWeekAdapter.ViewHolder> {
    public interface Action { void onSelect(StudentProgressWeekItem item); }
    private final List<StudentProgressWeekItem> items = new ArrayList<>();
    private final Action action;
    private String selectedWeekId;

    public StudentProgressWeekAdapter(Action action) { this.action = action; }

    public void submitList(List<StudentProgressWeekItem> values, String selected) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(values);
        selectedWeekId = selected;
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    public void setSelectedWeek(String weekId) {
        String previous = selectedWeekId;
        selectedWeekId = weekId;
        notifyWeek(previous);
        notifyWeek(weekId);
    }

    public void updateProgress(String weekId, th.ac.vu.classflow.data.model.StudentProgress progress) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).getWeek().getWeekId().equals(weekId)) {
                items.set(index, new StudentProgressWeekItem(items.get(index).getWeek(), progress));
                notifyItemChanged(index);
                return;
            }
        }
    }

    private void notifyWeek(String id) {
        if (id == null) return;
        for (int index = 0; index < items.size(); index++) {
            if (id.equals(items.get(index).getWeek().getWeekId())) notifyItemChanged(index);
        }
    }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_progress_week, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentProgressWeekItem item = items.get(position);
        holder.week.setText(String.format(Locale.US, "W%02d", item.getWeek().getWeekNumber()));
        holder.title.setText(item.getWeek().getTitle());
        holder.status.setText(ProgressPresentation.label(item.presentationStatus()));
        holder.card.setSelected(item.getWeek().getWeekId().equals(selectedWeekId));
        holder.card.setStrokeWidth(holder.card.isSelected() ? 4 : 1);
        holder.card.setOnClickListener(view -> action.onSelect(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final com.google.android.material.card.MaterialCardView card;
        final TextView week;
        final TextView title;
        final TextView status;
        ViewHolder(View view) {
            super(view);
            card = (com.google.android.material.card.MaterialCardView) view;
            week = view.findViewById(R.id.progress_week_number);
            title = view.findViewById(R.id.progress_week_title);
            status = view.findViewById(R.id.progress_week_status);
        }
    }
}
