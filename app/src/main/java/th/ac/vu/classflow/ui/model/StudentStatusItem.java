package th.ac.vu.classflow.ui.model;

import androidx.annotation.Nullable;

import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.StudentProgress;

public final class StudentStatusItem {
    private final Student student;
    private final Enrollment enrollment;
    @Nullable private final StudentProgress progress;

    public StudentStatusItem(Student student, Enrollment enrollment,
                             @Nullable StudentProgress progress) {
        this.student = student;
        this.enrollment = enrollment;
        this.progress = progress;
    }

    public Student getStudent() { return student; }
    public Enrollment getEnrollment() { return enrollment; }
    @Nullable public StudentProgress getProgress() { return progress; }
    public String presentationStatus() {
        return progress == null ? ProgressPresentation.NOT_RECORDED : progress.getOverallStatus();
    }
}
