package th.ac.vu.classflow.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import th.ac.vu.classflow.data.assessment.AssessmentCalculator;
import th.ac.vu.classflow.data.assessment.AssessmentRubricCatalog;
import th.ac.vu.classflow.data.model.AssessmentCalculation;
import th.ac.vu.classflow.data.model.AssessmentData;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.ui.model.EnrollmentCardItem;

public final class StudentLearningCardAdapter extends RecyclerView.Adapter<StudentLearningCardAdapter.ViewHolder> {

    public interface OnCardSelectedListener {
        void onCardSelected(EnrollmentCardItem item);
    }

    private final List<EnrollmentCardItem> items = new ArrayList<>();
    private final OnCardSelectedListener listener;
    @Nullable private String selectedEnrollmentId;

    public StudentLearningCardAdapter(OnCardSelectedListener listener) {
        this.listener = listener;
    }

    public void submitList(List<EnrollmentCardItem> values, @Nullable String selectedEnrollmentId) {
        this.selectedEnrollmentId = selectedEnrollmentId;
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        if (values != null) items.addAll(values);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    public void setSelectedEnrollmentId(@Nullable String selectedEnrollmentId) {
        this.selectedEnrollmentId = selectedEnrollmentId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_learning_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), selectedEnrollmentId, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static String formatAssessmentSummary(Context context, EnrollmentCardItem item) {
        String trackId = item.getEnrollment().getTrackId();
        if (!AssessmentRubricCatalog.TRACK_SS1.equalsIgnoreCase(trackId)) {
            return context.getString(R.string.rubric_not_configured);
        }
        AssessmentData assessmentData = item.getEnrollment().getParsedAssessment();
        if (assessmentData == null) {
            return context.getString(R.string.not_assessed_yet);
        }
        AssessmentCalculation calc = AssessmentCalculator.calculate(assessmentData);
        if (calc.isOverallAvailable()) {
            return String.format(Locale.US, "%s · %.2f", calc.getOverallLevel(), calc.getOverallScore());
        } else {
            return context.getString(R.string.not_enough_evidence);
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView classNameView;
        private final TextView trackView;
        private final Chip statusChip;
        private final Chip weekChip;
        private final Chip selectedChip;
        private final TextView assessmentSummaryView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.learning_card);
            classNameView = itemView.findViewById(R.id.learning_class_name);
            trackView = itemView.findViewById(R.id.learning_track);
            statusChip = itemView.findViewById(R.id.learning_status_chip);
            weekChip = itemView.findViewById(R.id.learning_week_chip);
            selectedChip = itemView.findViewById(R.id.learning_selected_chip);
            assessmentSummaryView = itemView.findViewById(R.id.learning_assessment_summary);
        }

        void bind(EnrollmentCardItem item, @Nullable String selectedEnrollmentId, OnCardSelectedListener listener) {
            Context context = itemView.getContext();
            classNameView.setText(item.getClassOffering().getName());
            trackView.setText(String.format(Locale.US, "%s · %s",
                    item.getTrack().getCode(), item.getTrack().getName()));

            String status = item.getEnrollment().getStatus();
            statusChip.setText(status);
            bindStatusChipColor(statusChip, status);

            if (Enrollment.STATUS_ACTIVE.equals(status) && item.getClassOffering().getCurrentWeek() > 0) {
                weekChip.setVisibility(View.VISIBLE);
                weekChip.setText(context.getString(R.string.class_current_week_badge,
                        item.getClassOffering().getCurrentWeek()));
            } else {
                weekChip.setVisibility(View.GONE);
            }

            boolean isSelected = selectedEnrollmentId != null
                    && selectedEnrollmentId.equals(item.getEnrollment().getEnrollmentId());
            selectedChip.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            float density = context.getResources().getDisplayMetrics().density;
            if (isSelected) {
                cardView.setStrokeWidth(Math.round(2 * density));
                cardView.setStrokeColor(ContextCompat.getColor(context, R.color.indigo_primary));
            } else {
                cardView.setStrokeWidth(Math.round(1 * density));
                cardView.setStrokeColor(ContextCompat.getColor(context, R.color.slate_light));
            }

            assessmentSummaryView.setText(formatAssessmentSummary(context, item));

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCardSelected(item);
                }
            });
        }

        private static void bindStatusChipColor(Chip chip, String status) {
            Context context = chip.getContext();
            int containerColorRes;
            int textColorRes;

            if (Enrollment.STATUS_ACTIVE.equals(status)) {
                containerColorRes = R.color.status_active_container;
                textColorRes = R.color.status_on_track;
            } else if (Enrollment.STATUS_COMPLETED.equals(status)) {
                containerColorRes = R.color.status_completed_container;
                textColorRes = R.color.status_completed;
            } else {
                containerColorRes = R.color.status_archived_container;
                textColorRes = R.color.slate_gray;
            }

            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, containerColorRes)));
            chip.setTextColor(ContextCompat.getColor(context, textColorRes));
        }
    }
}