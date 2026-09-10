package th.ac.vu.classflow.ui.model;

import androidx.annotation.Nullable;

import th.ac.vu.classflow.data.model.ProjectProgress;

public final class ProjectProgressWeekItem {
    private final int weekNumber;
    private final String weekId;
    private final String weekTitle;
    private final String progressType;
    @Nullable private final ProjectProgress progress;
    private final boolean isCurrentWeek;

    public ProjectProgressWeekItem(int weekNumber, String weekId, String weekTitle,
                                   String progressType, @Nullable ProjectProgress progress,
                                   boolean isCurrentWeek) {
        this.weekNumber = weekNumber;
        this.weekId = weekId;
        this.weekTitle = weekTitle;
        this.progressType = progressType;
        this.progress = progress;
        this.isCurrentWeek = isCurrentWeek;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public String getWeekId() {
        return weekId;
    }

    public String getWeekTitle() {
        return weekTitle;
    }

    public String getProgressType() {
        return progressType;
    }

    @Nullable
    public ProjectProgress getProgress() {
        return progress;
    }

    public boolean isCurrentWeek() {
        return isCurrentWeek;
    }
}
