package th.ac.vu.classflow.ui.model;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.assessment.AssessmentRubricCatalog;
import th.ac.vu.classflow.data.model.Enrollment;

public final class LearningPathSummary {

    private final String summaryText;
    private final boolean visible;

    public interface StringResolver {
        String getString(int resId);
        String getString(int resId, Object... formatArgs);
    }

    public LearningPathSummary(@Nullable String summaryText, boolean visible) {
        this.summaryText = summaryText;
        this.visible = visible;
    }

    @Nullable
    public String getSummaryText() {
        return summaryText;
    }

    public boolean isVisible() {
        return visible;
    }

    public static LearningPathSummary from(Context context, @Nullable List<EnrollmentCardItem> items) {
        if (context == null) {
            return new LearningPathSummary(null, false);
        }
        return from(new StringResolver() {
            @Override
            public String getString(int resId) {
                return context.getString(resId);
            }

            @Override
            public String getString(int resId, Object... formatArgs) {
                return context.getString(resId, formatArgs);
            }
        }, items);
    }

    public static LearningPathSummary from(StringResolver resolver, @Nullable List<EnrollmentCardItem> items) {
        if (resolver == null || items == null || items.isEmpty()) {
            return new LearningPathSummary(null, false);
        }

        List<EnrollmentCardItem> ss1List = new ArrayList<>();
        List<EnrollmentCardItem> ss2List = new ArrayList<>();

        for (EnrollmentCardItem item : items) {
            if (item == null || item.getEnrollment() == null) continue;
            String trackId = item.getEnrollment().getTrackId();
            String trackCode = item.getTrack() != null ? item.getTrack().getCode() : trackId;

            if (AssessmentRubricCatalog.TRACK_SS1.equalsIgnoreCase(trackId)
                    || "SS1".equalsIgnoreCase(trackCode)) {
                ss1List.add(item);
            } else if ("SS2".equalsIgnoreCase(trackId)
                    || "SS2".equalsIgnoreCase(trackCode)) {
                ss2List.add(item);
            }
        }

        if (ss1List.isEmpty() && ss2List.isEmpty()) {
            return new LearningPathSummary(null, false);
        }

        boolean hasSs1Completed = false;
        boolean hasSs1Active = false;
        String ss1CompletedClassName = null;
        String ss1ActiveClassName = null;

        for (EnrollmentCardItem e : ss1List) {
            String status = e.getEnrollment().getStatus();
            if (Enrollment.STATUS_COMPLETED.equals(status)) {
                hasSs1Completed = true;
                if (ss1CompletedClassName == null) {
                    ss1CompletedClassName = e.getClassOffering().getName();
                }
            } else if (Enrollment.STATUS_ACTIVE.equals(status)) {
                hasSs1Active = true;
                if (ss1ActiveClassName == null) {
                    ss1ActiveClassName = e.getClassOffering().getName();
                }
            }
        }

        boolean hasSs2Active = false;
        boolean hasSs2Completed = false;
        String ss2ActiveClassName = null;
        String ss2CompletedClassName = null;

        for (EnrollmentCardItem e : ss2List) {
            String status = e.getEnrollment().getStatus();
            if (Enrollment.STATUS_ACTIVE.equals(status)) {
                hasSs2Active = true;
                if (ss2ActiveClassName == null) {
                    ss2ActiveClassName = e.getClassOffering().getName();
                }
            } else if (Enrollment.STATUS_COMPLETED.equals(status)) {
                hasSs2Completed = true;
                if (ss2CompletedClassName == null) {
                    ss2CompletedClassName = e.getClassOffering().getName();
                }
            }
        }

        boolean multipleRecords = ss1List.size() > 1 || ss2List.size() > 1;
        String suffix = multipleRecords ? resolver.getString(R.string.learning_path_multiple_suffix) : "";

        // Case A: SS1 completed and SS2 active
        if (hasSs1Completed && hasSs2Active) {
            String text = resolver.getString(R.string.learning_path_case_a,
                    ss1CompletedClassName != null ? ss1CompletedClassName : "SS1",
                    resolver.getString(R.string.completed),
                    ss2ActiveClassName != null ? ss2ActiveClassName : "SS2",
                    resolver.getString(R.string.active)) + suffix;
            return new LearningPathSummary(text, true);
        }

        // Case B: SS1 completed and no SS2 enrollment
        if (hasSs1Completed && ss2List.isEmpty()) {
            String text = resolver.getString(R.string.learning_path_case_b,
                    ss1CompletedClassName != null ? ss1CompletedClassName : "SS1",
                    resolver.getString(R.string.completed)) + suffix;
            return new LearningPathSummary(text, true);
        }

        // Case C: SS1 active and no SS2 enrollment
        if (hasSs1Active && ss2List.isEmpty()) {
            String text = resolver.getString(R.string.learning_path_case_c,
                    ss1ActiveClassName != null ? ss1ActiveClassName : "SS1",
                    resolver.getString(R.string.active)) + suffix;
            return new LearningPathSummary(text, true);
        }

        // Case D: SS2 active and no SS1 enrollment
        if (hasSs2Active && ss1List.isEmpty()) {
            String text = resolver.getString(R.string.learning_path_case_d,
                    ss2ActiveClassName != null ? ss2ActiveClassName : "SS2",
                    resolver.getString(R.string.active)) + suffix;
            return new LearningPathSummary(text, true);
        }

        // Generic multi-track or other statuses (e.g. SS1 + SS2 both completed, withdrawn, etc.)
        StringBuilder sb = new StringBuilder();
        if (!ss1List.isEmpty()) {
            EnrollmentCardItem first = ss1List.get(0);
            sb.append(first.getClassOffering().getName())
              .append(" (SS1) [").append(first.getEnrollment().getStatus()).append("]");
        }
        if (!ss2List.isEmpty()) {
            if (sb.length() > 0) sb.append(" → ");
            EnrollmentCardItem first = ss2List.get(0);
            sb.append(first.getClassOffering().getName())
              .append(" (SS2) [").append(first.getEnrollment().getStatus()).append("]");
        }
        sb.append(suffix);
        return new LearningPathSummary(sb.toString(), true);
    }
}