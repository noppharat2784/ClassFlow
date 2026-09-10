package th.ac.vu.classflow.data.model;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AssessmentCalculation {

    public static final String LEVEL_STRONG = "STRONG";
    public static final String LEVEL_READY = "READY";
    public static final String LEVEL_DEVELOPING = "DEVELOPING";
    public static final String LEVEL_NEED_SUPPORT = "NEED SUPPORT";
    public static final String LEVEL_INSUFFICIENT_EVIDENCE = "INSUFFICIENT EVIDENCE";

    private final List<DomainResult> domainResults;
    @Nullable
    private final Double overallScore;
    private final boolean isOverallAvailable;
    @Nullable
    private final String level;

    public AssessmentCalculation(List<DomainResult> domainResults,
                                 @Nullable Double overallScore,
                                 boolean isOverallAvailable,
                                 @Nullable String level) {
        this.domainResults = Collections.unmodifiableList(domainResults);
        this.overallScore = overallScore;
        this.isOverallAvailable = isOverallAvailable;
        this.level = level;
    }

    public List<DomainResult> getDomainResults() {
        return domainResults;
    }

    @Nullable
    public Double getOverallScore() {
        return overallScore;
    }

    public boolean isOverallAvailable() {
        return isOverallAvailable;
    }

    @Nullable
    public String getLevel() {
        return level;
    }

    public String getOverallLevel() {
        return level != null ? level : LEVEL_INSUFFICIENT_EVIDENCE;
    }

    public int getObservedCriteriaCount() {
        int count = 0;
        for (DomainResult dr : domainResults) {
            count += dr.getObservedCount();
        }
        return count;
    }

    public int getTotalCriteriaCount() {
        int count = 0;
        for (DomainResult dr : domainResults) {
            count += dr.getTotalCount();
        }
        return count;
    }

    public int getCriteriaCoveragePercent() {
        int total = getTotalCriteriaCount();
        if (total == 0) return 0;
        return Math.round(((float) getObservedCriteriaCount() * 100f) / total);
    }

    public int getAvailableDomainCount() {
        int count = 0;
        for (DomainResult dr : domainResults) {
            if (dr.isAvailable()) count++;
        }
        return count;
    }

    public int getTotalDomainCount() {
        return domainResults.size();
    }

    public String getFormattedOverallScore() {
        if (!isOverallAvailable || overallScore == null) {
            return "Not enough evidence";
        }
        return String.format(Locale.US, "%.2f", overallScore);
    }
}
