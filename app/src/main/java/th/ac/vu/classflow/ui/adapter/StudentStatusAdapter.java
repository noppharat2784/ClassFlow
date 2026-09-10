package th.ac.vu.classflow.ui.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.StudentStatusItem;

public final class StudentStatusAdapter extends RecyclerView.Adapter<StudentStatusAdapter.ViewHolder> {
    public interface Action { void onOpen(StudentStatusItem item); }
    private final List<StudentStatusItem> items = new ArrayList<>();
    private final Action action;

    public StudentStatusAdapter(Action action) { this.action = action; }

    public void submitList(List<StudentStatusItem> values) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(values);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_status, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentStatusItem item = items.get(position);
        holder.name.setText(item.getStudent().getName());
        String nickname = item.getStudent().getNickname();
        holder.nickname.setText(nickname);
        holder.nickname.setVisibility(nickname == null || nickname.isEmpty() ? View.GONE : View.VISIBLE);
        String status = item.presentationStatus();
        holder.status.setText(ProgressPresentation.label(status));
        holder.status.setTextColor(ContextCompat.getColor(holder.status.getContext(), colorFor(status)));
        holder.status.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(
                holder.status.getContext(), containerFor(status))));
        holder.itemView.setOnClickListener(view -> action.onOpen(item));
    }

    private int colorFor(String status) {
        if (StudentProgress.STATUS_ON_TRACK.equals(status)) return R.color.status_on_track;
        if (StudentProgress.STATUS_NEEDS_ATTENTION.equals(status)) return R.color.status_needs_attention;
        if (StudentProgress.STATUS_BLOCKED.equals(status)) return R.color.status_blocked;
        if (StudentProgress.STATUS_COMPLETED.equals(status)) return R.color.status_completed;
        return R.color.status_not_started;
    }

    private int containerFor(String status) {
        if (StudentProgress.STATUS_ON_TRACK.equals(status)) return R.color.status_active_container;
        if (StudentProgress.STATUS_NEEDS_ATTENTION.equals(status)) return R.color.status_attention_container;
        if (StudentProgress.STATUS_BLOCKED.equals(status)) return R.color.status_blocked_container;
        if (StudentProgress.STATUS_COMPLETED.equals(status)) return R.color.status_completed_container;
        return R.color.status_archived_container;
    }

    @Override public int getItemCount() { return items.size(); }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView nickname;
        final Chip status;
        ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.student_status_name);
            nickname = view.findViewById(R.id.student_status_nickname);
            status = view.findViewById(R.id.student_status_chip);
        }
    }
}
