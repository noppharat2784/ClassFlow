package th.ac.vu.classflow.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.ui.model.EnrollmentCardItem;

public final class EnrollmentManageAdapter extends RecyclerView.Adapter<EnrollmentManageAdapter.ViewHolder> {
    public interface Actions { void onSaveStatus(EnrollmentCardItem item, String status); }
    private final List<EnrollmentCardItem> items = new ArrayList<>();
    private final Map<String, String> pendingStatuses = new HashMap<>();
    private final Actions actions;
    private String busyId;

    public EnrollmentManageAdapter(Actions actions) { this.actions = actions; }
    public void submitList(List<EnrollmentCardItem> values) {
        int oldSize = items.size();
        pendingStatuses.clear();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(values);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }
    public void setBusy(String id) {
        String previous = busyId;
        busyId = id;
        notifyEnrollmentChanged(previous);
        notifyEnrollmentChanged(id);
    }
    public void clearPending(String id) {
        if (id != null) {
            pendingStatuses.remove(id);
            notifyEnrollmentChanged(id);
        } else {
            pendingStatuses.clear();
            notifyDataSetChanged();
        }
    }

    private void notifyEnrollmentChanged(String id) {
        if (id == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(items.get(i).getEnrollment().getEnrollmentId())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manage_enrollment, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EnrollmentCardItem item = items.get(position);
        Enrollment enrollment = item.getEnrollment();
        holder.className.setText(item.getClassOffering().getName());
        holder.trackWeek.setText(holder.trackWeek.getContext().getString(
                R.string.enrollment_track_week_class_id_format,
                item.getTrack().getCode(), item.getTrack().getName(),
                item.getClassOffering().getCurrentWeek(), enrollment.getClassId()));
        List<String> statuses = Arrays.asList(Enrollment.STATUS_ACTIVE,
                Enrollment.STATUS_COMPLETED, Enrollment.STATUS_WITHDRAWN,
                Enrollment.STATUS_ARCHIVED);
        holder.status.setAdapter(new ArrayAdapter<>(holder.status.getContext(),
                android.R.layout.simple_list_item_1, statuses));
        holder.status.setThreshold(0);
        final String effectiveStatus = pendingStatuses.containsKey(enrollment.getEnrollmentId())
                ? pendingStatuses.get(enrollment.getEnrollmentId())
                : enrollment.getStatus();
        final String[] selected = {effectiveStatus};
        holder.status.setText(effectiveStatus, false);
        holder.status.setOnClickListener(ignored -> holder.status.showDropDown());
        holder.status.setOnDismissListener(() -> {
            if (holder.status.getText().length() == 0) {
                holder.status.setText(selected[0], false);
            }
        });
        holder.status.setOnItemClickListener((parent, view, selectedPosition, id) -> {
            String newStatus = statuses.get(selectedPosition);
            selected[0] = newStatus;
            if (!newStatus.equals(enrollment.getStatus())) {
                pendingStatuses.put(enrollment.getEnrollmentId(), newStatus);
                actions.onSaveStatus(item, newStatus);
            }
        });
        boolean busy = enrollment.getEnrollmentId().equals(busyId);
        holder.status.setEnabled(!busy);
        holder.save.setEnabled(!busy && !enrollment.getStatus().equals(selected[0]));
        holder.save.setOnClickListener(view -> {
            if (!selected[0].equals(enrollment.getStatus())) {
                pendingStatuses.put(enrollment.getEnrollmentId(), selected[0]);
                actions.onSaveStatus(item, selected[0]);
            }
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView className;
        final TextView trackWeek;
        final AutoCompleteTextView status;
        final MaterialButton save;
        ViewHolder(View view) {
            super(view);
            className = view.findViewById(R.id.manage_enrollment_class);
            trackWeek = view.findViewById(R.id.manage_enrollment_track_week);
            status = view.findViewById(R.id.manage_enrollment_status);
            save = view.findViewById(R.id.manage_enrollment_save_status);
        }
    }
}
