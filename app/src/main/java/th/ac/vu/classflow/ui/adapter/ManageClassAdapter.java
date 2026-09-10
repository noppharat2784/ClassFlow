package th.ac.vu.classflow.ui.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.ui.model.ClassCardItem;

public final class ManageClassAdapter
        extends RecyclerView.Adapter<ManageClassAdapter.ViewHolder> {

    public static final String FILTER_ALL = "ALL";

    public interface Actions {
        void onEdit(ClassCardItem item);

        void onLifecycleChange(ClassCardItem item, String targetStatus);
    }

    private final List<ClassCardItem> allItems = new ArrayList<>();
    private final List<ClassCardItem> visibleItems = new ArrayList<>();
    private final Set<String> busyClassIds = new HashSet<>();
    private final Actions actions;
    private String filter = ClassOffering.STATUS_ACTIVE;

    public ManageClassAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submitList(List<ClassCardItem> items) {
        allItems.clear();
        allItems.addAll(items);
        rebuildVisibleItems();
    }

    public void setFilter(String selectedFilter) {
        filter = selectedFilter;
        rebuildVisibleItems();
    }

    public String getFilter() {
        return filter;
    }

    public void setClassBusy(String classId, boolean busy) {
        if (busy) {
            busyClassIds.add(classId);
        } else {
            busyClassIds.remove(classId);
        }
        int position = visiblePosition(classId);
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    public void updateStatus(String classId, String status) {
        for (ClassCardItem item : allItems) {
            if (classId.equals(item.getClassOffering().getClassId())) {
                item.getClassOffering().setStatus(status);
                break;
            }
        }
        busyClassIds.remove(classId);
        rebuildVisibleItems();
    }

    public boolean isEmpty() {
        return visibleItems.isEmpty();
    }

    private void rebuildVisibleItems() {
        int oldSize = visibleItems.size();
        visibleItems.clear();
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize);
        }
        for (ClassCardItem item : allItems) {
            if (FILTER_ALL.equals(filter)
                    || filter.equals(item.getClassOffering().getStatus())) {
                visibleItems.add(item);
            }
        }
        visibleItems.sort((left, right) -> {
            if (FILTER_ALL.equals(filter)) {
                int statusComparison = Integer.compare(
                        statusRank(left.getClassOffering().getStatus()),
                        statusRank(right.getClassOffering().getStatus()));
                if (statusComparison != 0) {
                    return statusComparison;
                }
            }
            return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER).compare(
                    left.getClassOffering().getName(),
                    right.getClassOffering().getName());
        });
        if (!visibleItems.isEmpty()) {
            notifyItemRangeInserted(0, visibleItems.size());
        }
    }

    private int statusRank(String status) {
        if (ClassOffering.STATUS_ACTIVE.equals(status)) {
            return 0;
        }
        if (ClassOffering.STATUS_PLANNED.equals(status)) {
            return 1;
        }
        if (ClassOffering.STATUS_COMPLETED.equals(status)) {
            return 2;
        }
        return 3;
    }

    private int visiblePosition(String classId) {
        for (int index = 0; index < visibleItems.size(); index++) {
            if (classId.equals(visibleItems.get(index).getClassOffering().getClassId())) {
                return index;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manage_class, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClassCardItem item = visibleItems.get(position);
        ClassOffering classOffering = item.getClassOffering();
        holder.name.setText(classOffering.getName());
        holder.track.setText(String.format(Locale.getDefault(), "%s · %s",
                item.getTrack().getCode(), item.getTrack().getName()));
        holder.week.setText(String.format(Locale.getDefault(), "Week %d — %s",
                item.getCurrentWeek().getWeekNumber(), item.getCurrentWeek().getTitle()));
        bindStatus(holder.status, classOffering.getStatus());

        boolean busy = busyClassIds.contains(classOffering.getClassId());
        holder.more.setEnabled(!busy);
        holder.more.setAlpha(busy ? 0.4f : 1f);
        holder.more.setOnClickListener(view -> showActions(holder.more, item));
    }

    private void showActions(View anchor, ClassCardItem item) {
        PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);
        popupMenu.inflate(R.menu.class_row_actions);
        String status = item.getClassOffering().getStatus();

        popupMenu.getMenu().findItem(R.id.action_class_activate).setVisible(
                ClassOffering.STATUS_PLANNED.equals(status)
                        || ClassOffering.STATUS_COMPLETED.equals(status));
        popupMenu.getMenu().findItem(R.id.action_class_complete).setVisible(
                ClassOffering.STATUS_ACTIVE.equals(status));
        popupMenu.getMenu().findItem(R.id.action_class_archive).setVisible(
                !ClassOffering.STATUS_ARCHIVED.equals(status));
        popupMenu.getMenu().findItem(R.id.action_class_restore).setVisible(
                ClassOffering.STATUS_ARCHIVED.equals(status));
        if (ClassOffering.STATUS_COMPLETED.equals(status)) {
            popupMenu.getMenu().findItem(R.id.action_class_activate)
                    .setTitle(R.string.reactivate);
        }

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.action_class_edit) {
                actions.onEdit(item);
                return true;
            }
            if (itemId == R.id.action_class_activate) {
                actions.onLifecycleChange(item, ClassOffering.STATUS_ACTIVE);
                return true;
            }
            if (itemId == R.id.action_class_complete) {
                actions.onLifecycleChange(item, ClassOffering.STATUS_COMPLETED);
                return true;
            }
            if (itemId == R.id.action_class_archive) {
                actions.onLifecycleChange(item, ClassOffering.STATUS_ARCHIVED);
                return true;
            }
            if (itemId == R.id.action_class_restore) {
                actions.onLifecycleChange(item, ClassOffering.STATUS_PLANNED);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void bindStatus(Chip chip, String status) {
        chip.setText(status);
        int background;
        int foreground;
        if (ClassOffering.STATUS_ACTIVE.equals(status)) {
            background = R.color.status_active_container;
            foreground = R.color.status_on_track;
        } else if (ClassOffering.STATUS_PLANNED.equals(status)) {
            background = R.color.status_planned_container;
            foreground = R.color.status_not_started;
        } else if (ClassOffering.STATUS_COMPLETED.equals(status)) {
            background = R.color.status_completed_container;
            foreground = R.color.status_completed;
        } else {
            background = R.color.status_archived_container;
            foreground = R.color.slate_gray;
        }
        chip.setChipBackgroundColor(ColorStateList.valueOf(
                ContextCompat.getColor(chip.getContext(), background)));
        chip.setTextColor(ContextCompat.getColor(chip.getContext(), foreground));
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView track;
        private final TextView week;
        private final Chip status;
        private final ImageButton more;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.manage_class_name);
            track = itemView.findViewById(R.id.manage_class_track);
            week = itemView.findViewById(R.id.manage_class_week);
            status = itemView.findViewById(R.id.manage_class_status);
            more = itemView.findViewById(R.id.manage_class_more);
        }
    }
}
