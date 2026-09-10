package th.ac.vu.classflow.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.model.CurriculumWeek;

public final class CurriculumWeekAdapter
        extends RecyclerView.Adapter<CurriculumWeekAdapter.ViewHolder> {

    public interface OnWeekClickListener {
        void onWeekClick(CurriculumWeek week);
    }

    private final List<CurriculumWeek> weeks = new ArrayList<>();
    private final OnWeekClickListener listener;
    private int currentWeek;

    public CurriculumWeekAdapter(OnWeekClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<CurriculumWeek> items, int selectedCurrentWeek) {
        int oldSize = weeks.size();
        weeks.clear();
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize);
        }
        weeks.addAll(items);
        currentWeek = selectedCurrentWeek;
        if (!items.isEmpty()) {
            notifyItemRangeInserted(0, items.size());
        }
    }

    public void setCurrentWeek(int selectedCurrentWeek) {
        currentWeek = selectedCurrentWeek;
        notifyItemRangeChanged(0, weeks.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_curriculum_week, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CurriculumWeek week = weeks.get(position);
        holder.weekNumber.setText(String.format(Locale.US, "W%02d", week.getWeekNumber()));
        holder.title.setText(week.getTitle());
        holder.progressType.setText(week.getProgressType());

        if (week.getWeekNumber() < currentWeek) {
            bindVisualState(holder, R.string.week_state_past,
                    R.color.status_completed, R.color.white, 1);
        } else if (week.getWeekNumber() == currentWeek) {
            bindVisualState(holder, R.string.week_state_current,
                    R.color.indigo_primary, R.color.soft_blue, 2);
        } else {
            bindVisualState(holder, R.string.week_state_upcoming,
                    R.color.slate_light, R.color.white, 1);
        }
        holder.itemView.setOnClickListener(view -> listener.onWeekClick(week));
    }

    private void bindVisualState(ViewHolder holder, int labelResource, int strokeResource,
                                 int backgroundResource, int strokeWidthDp) {
        holder.state.setText(labelResource);
        holder.card.setStrokeColor(ContextCompat.getColor(holder.card.getContext(), strokeResource));
        holder.card.setCardBackgroundColor(ContextCompat.getColor(
                holder.card.getContext(), backgroundResource));
        float density = holder.card.getResources().getDisplayMetrics().density;
        holder.card.setStrokeWidth(Math.round(strokeWidthDp * density));
        holder.state.setTextColor(ContextCompat.getColor(holder.card.getContext(),
                strokeResource == R.color.slate_light ? R.color.slate_gray : strokeResource));
    }

    @Override
    public int getItemCount() {
        return weeks.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView card;
        private final TextView weekNumber;
        private final TextView title;
        private final TextView progressType;
        private final TextView state;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            weekNumber = itemView.findViewById(R.id.curriculum_week_number);
            title = itemView.findViewById(R.id.curriculum_week_title);
            progressType = itemView.findViewById(R.id.curriculum_week_progress_type);
            state = itemView.findViewById(R.id.curriculum_week_state);
        }
    }
}
