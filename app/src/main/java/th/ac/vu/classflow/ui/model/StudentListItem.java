package th.ac.vu.classflow.ui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.StudentProgress;

public final class StudentListItem {
    private final Student student;
    private final List<EnrollmentCardItem> enrollments;
    private final Map<String, StudentProgress> currentProgressByClass;

    public StudentListItem(Student student, List<EnrollmentCardItem> enrollments) {
        this(student, enrollments, new LinkedHashMap<>());
    }

    public StudentListItem(Student student, List<EnrollmentCardItem> enrollments,
                           Map<String, StudentProgress> currentProgressByClass) {
        this.student = student;
        this.enrollments = Collections.unmodifiableList(new ArrayList<>(enrollments));
        this.currentProgressByClass = Collections.unmodifiableMap(
                new LinkedHashMap<>(currentProgressByClass));
    }

    public Student getStudent() { return student; }
    public List<EnrollmentCardItem> getEnrollments() { return enrollments; }
    public StudentProgress getCurrentProgress(String classId) {
        return currentProgressByClass.get(classId);
    }

    public List<EnrollmentCardItem> getActiveEnrollments() {
        List<EnrollmentCardItem> result = new ArrayList<>();
        for (EnrollmentCardItem item : enrollments) {
            if (Enrollment.STATUS_ACTIVE.equals(item.getEnrollment().getStatus())) result.add(item);
        }
        return result;
    }
}
