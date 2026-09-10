package th.ac.vu.classflow.ui.adapter;

import android.content.Context;
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

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.ProjectCardItem;

public final class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ViewHolder> {

    public interface Actions {
        void onOpen(ProjectCardItem item);
        void onEdit(ProjectCardItem item);
        void onArchive(ProjectCardItem item);
    }

    private final List<ProjectCardItem> items = new ArrayList<>();
    private final Actions actions;

    public ProjectAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submitList(List<ProjectCardItem> values) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        if (values != null) items.addAll(values);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), actions);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView teamNameView;
        private final TextView contextView;
        private final Chip statusChip;
        private final Chip archivedBadge;
        private final MaterialCardView blockerCard;
        private final TextView blockerText;
        private final TextView membersView;
        private final ImageButton moreButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.project_title);
            teamNameView = itemView.findViewById(R.id.project_team_name);
            contextView = itemView.findViewById(R.id.project_context);
            statusChip = itemView.findViewById(R.id.project_status_chip);
            archivedBadge = itemView.findViewById(R.id.project_archived_badge);
            blockerCard = itemView.findViewById(R.id.project_blocker_card);
            blockerText = itemView.findViewById(R.id.project_blocker_text);
            membersView = itemView.findViewById(R.id.project_members);
            moreButton = itemView.findViewById(R.id.project_more);
        }

        void bind(ProjectCardItem item, Actions actions) {
            Context context = itemView.getContext();
            Project project = item.getProject();

            titleView.setText(project.getTitle());

            if (project.getTeamName() != null && !project.getTeamName().trim().isEmpty()) {
                teamNameView.setText(project.getTeamName().trim());
                teamNameView.setVisibility(View.VISIBLE);
            } else {
                teamNameView.setVisibility(View.GONE);
            }

            String trackCode = item.getTrack() != null ? item.getTrack().getCode() : project.getTrackId().toUpperCase(Locale.US);
            String className = item.getClassOffering() != null ? item.getClassOffering().getName() : project.getClassId();
            contextView.setText(String.format(Locale.US, "%s · %s · Week %d",
                    trackCode, className, project.getCurrentWeek()));

            statusChip.setText(ProgressPresentation.label(project.getOverallStatus()));
            bindStatusChipColor(statusChip, project.getOverallStatus());

            if (!project.isActive()) {
                archivedBadge.setVisibility(View.VISIBLE);
            } else {
                archivedBadge.setVisibility(View.GONE);
            }

            if (Project.STATUS_BLOCKED.equals(project.getOverallStatus())
                    && item.getCurrentBlocker() != null
                    && !item.getCurrentBlocker().trim().isEmpty()) {
                blockerCard.setVisibility(View.VISIBLE);
                blockerText.setText(context.getString(R.string.current_blocker_format, item.getCurrentBlocker().trim()));
            } else {
                blockerCard.setVisibility(View.GONE);
            }

            StringBuilder membersBuilder = new StringBuilder();
            List<Student> members = item.getMembers();
            if (members.isEmpty()) {
                membersBuilder.append(context.getString(R.string.no_members_selected));
            } else {
                for (int i = 0; i < members.size(); i++) {
                    Student s = members.get(i);
                    if (i > 0) membersBuilder.append(", ");
                    String displayName = (s.getNickname() != null && !s.getNickname().trim().isEmpty())
                            ? s.getNickname().trim() : s.getName();
                    membersBuilder.append(displayName);
                    if (!s.isActive()) {
                        membersBuilder.append(" ").append(context.getString(R.string.member_archived_label));
                    }
                }
            }
            membersView.setText(context.getString(R.string.project_members_format, membersBuilder.toString()));

            itemView.setOnClickListener(v -> actions.onOpen(item));

            moreButton.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, moreButton);
                popup.getMenu().add(0, 1, 0, R.string.open_details);
                popup.getMenu().add(0, 2, 1, R.string.edit_project);
                if (project.isActive()) {
                    popup.getMenu().add(0, 3, 2, R.string.archive_project);
                }
                popup.setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getItemId() == 1) {
                        actions.onOpen(item);
                        return true;
                    } else if (menuItem.getItemId() == 2) {
                        actions.onEdit(item);
                        return true;
                    } else if (menuItem.getItemId() == 3) {
                        actions.onArchive(item);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
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
