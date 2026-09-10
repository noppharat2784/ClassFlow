package th.ac.vu.classflow.ui.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Track;

public final class DashboardClassSummary {

    private final ClassOffering classOffering;
    private final Track track;
    private final CurriculumWeek currentWeek;
    private final int activeEnrollmentCount;
    private final int blockedCount;
    private final int needsAttentionCount;
    private final boolean partiallyUnavailable;

    public DashboardClassSummary(@NonNull ClassOffering classOffering,
                                 @NonNull Track track,
                                 @NonNull CurriculumWeek currentWeek,
                                 int activeEnrollmentCount,
                                 int blockedCount,
                                 int needsAttentionCount,
                                 boolean partiallyUnavailable) {
        this.classOffering = classOffering;
        this.track = track;
        this.currentWeek = currentWeek;
        this.activeEnrollmentCount = activeEnrollmentCount;
        this.blockedCount = blockedCount;
        this.needsAttentionCount = needsAttentionCount;
        this.partiallyUnavailable = partiallyUnavailable;
    }

    @NonNull
    public ClassOffering getClassOffering() {
        return classOffering;
    }

    @NonNull
    public Track getTrack() {
        return track;
    }

    @NonNull
    public CurriculumWeek getCurrentWeek() {
        return currentWeek;
    }

    public int getActiveEnrollmentCount() {
        return activeEnrollmentCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    public int getNeedsAttentionCount() {
        return needsAttentionCount;
    }

    public boolean isPartiallyUnavailable() {
        return partiallyUnavailable;
    }
}
