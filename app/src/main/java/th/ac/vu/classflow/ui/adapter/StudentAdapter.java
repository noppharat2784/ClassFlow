package th.ac.vu.classflow.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.ui.model.EnrollmentCardItem;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.StudentListItem;

public final class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {

    public interface Actions {
        void onOpen(StudentListItem item);
        void onEdit(StudentListItem item);
        void onManageEnrollment(StudentListItem item);
        void onArchive(StudentListItem item);
        void onRestore(StudentListItem item);
    }

    private final List<StudentListItem> items = new ArrayList<>();
    private final Actions actions;
    private String busyStudentId;

    public StudentAdapter(Actions actions) { this.actions = actions; }

    public void submitList(List<StudentListItem> values) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(values);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    public void setBusy(String studentId) {
        String previous = busyStudentId;
        busyStudentId = studentId;
        notifyStudentChanged(previous);
        notifyStudentChanged(studentId);
    }

    private void notifyStudentChanged(String studentId) {
        if (studentId == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (studentId.equals(items.get(i).getStudent().getStudentId())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudentListItem item = items.get(position);
        boolean archived = !item.getStudent().isActive();
        boolean busy = item.getStudent().getStudentId().equals(busyStudentId);
        holder.name.setText(item.getStudent().getName());
        String nickname = item.getStudent().getNickname();
        holder.nickname.setText(nickname);
        holder.nickname.setVisibility(nickname == null || nickname.isEmpty() ? View.GONE : View.VISIBLE);
        holder.studentId.setText(item.getStudent().getStudentId());
        holder.archiveBadge.setVisibility(archived ? View.VISIBLE : View.GONE);
        holder.summary.setText(enrollmentSummary(item));
        holder.more.setEnabled(!busy);
        holder.more.setAlpha(busy ? 0.4f : 1f);
        holder.more.setOnClickListener(view -> showMenu(holder.more, item));
        holder.itemView.setEnabled(!busy);
        holder.itemView.setOnClickListener(view -> actions.onOpen(item));
    }

    private String enrollmentSummary(StudentListItem student) {
        List<EnrollmentCardItem> active = student.getActiveEnrollments();
        if (active.isEmpty()) return "No active classes";
        StringBuilder value = new StringBuilder();
        if (active.size() > 1) value.append(active.size()).append(" active classes\n");
        for (int index = 0; index < active.size(); index++) {
            EnrollmentCardItem item = active.get(index);
            if (index > 0) value.append('\n');
            th.ac.vu.classflow.data.model.StudentProgress progress =
                    student.getCurrentProgress(item.getEnrollment().getClassId());
            String status = progress == null ? ProgressPresentation.NOT_RECORDED
                    : progress.getOverallStatus();
            value.append(String.format(Locale.getDefault(), "%s · %s · W%d — %s",
                    item.getTrack().getCode(), item.getClassOffering().getName(),
                    item.getClassOffering().getCurrentWeek(), ProgressPresentation.label(status)));
        }
        return value.toString();
    }

    private void showMenu(View anchor, StudentListItem item) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.inflate(R.menu.student_row_actions);
        boolean active = item.getStudent().isActive();
        menu.getMenu().findItem(R.id.action_student_edit).setVisible(active);
        menu.getMenu().findItem(R.id.action_manage_enrollment).setVisible(active);
        menu.getMenu().findItem(R.id.action_student_archive).setVisible(active);
        menu.getMenu().findItem(R.id.action_student_restore).setVisible(!active);
        menu.setOnMenuItemClickListener(selected -> {
            int id = selected.getItemId();
            if (id == R.id.action_student_open) actions.onOpen(item);
            else if (id == R.id.action_student_edit) actions.onEdit(item);
            else if (id == R.id.action_manage_enrollment) actions.onManageEnrollment(item);
            else if (id == R.id.action_student_archive) actions.onArchive(item);
            else if (id == R.id.action_student_restore) actions.onRestore(item);
            else return false;
            return true;
        });
        menu.show();
    }

    @Override public int getItemCount() { return items.size(); }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView nickname;
        final TextView studentId;
        final TextView summary;
        final Chip archiveBadge;
        final ImageButton more;

        ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.student_name);
            nickname = view.findViewById(R.id.student_nickname);
            studentId = view.findViewById(R.id.student_id);
            summary = view.findViewById(R.id.student_enrollment_summary);
            archiveBadge = view.findViewById(R.id.student_archived_badge);
            more = view.findViewById(R.id.student_more);
        }
    }
}
