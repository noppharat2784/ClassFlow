package th.ac.vu.classflow.ui.model;

import androidx.annotation.Nullable;

import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.StudentProgress;

public final class StudentProgressWeekItem {
    private final CurriculumWeek week;
    @Nullable private final StudentProgress progress;

    public StudentProgressWeekItem(CurriculumWeek week, @Nullable StudentProgress progress) {
        this.week = week;
        this.progress = progress;
    }

    public CurriculumWeek getWeek() { return week; }
    @Nullable public StudentProgress getProgress() { return progress; }
    public String presentationStatus() {
        return progress == null ? ProgressPresentation.NOT_RECORDED : progress.getOverallStatus();
    }
}
