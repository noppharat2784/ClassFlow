package th.ac.vu.classflow.data.model;

import androidx.annotation.Nullable;

import java.util.Locale;

public final class DomainResult {

    private final int domainId;
    private final String domainTitle;
    @Nullable
    private final Double score;
    private final int observedCount;
    private final int totalCount;
    private final boolean isAvailable;

    public DomainResult(int domainId, String domainTitle, @Nullable Double score,
                        int observedCount, int totalCount, boolean isAvailable) {
        this.domainId = domainId;
        this.domainTitle = domainTitle;
        this.score = score;
        this.observedCount = observedCount;
        this.totalCount = totalCount;
        this.isAvailable = isAvailable;
    }

    public int getDomainId() {
        return domainId;
    }

    public String getDomainTitle() {
        return domainTitle;
    }

    @Nullable
    public Double getScore() {
        return score;
    }

    public int getObservedCount() {
        return observedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public String getFormattedScore() {
        if (!isAvailable || score == null) {
            return "N/O";
        }
        return String.format(Locale.US, "%.2f", score);
    }

    public String getCoverageText() {
        return String.format(Locale.US, "%d/%d observed", observedCount, totalCount);
    }
}
