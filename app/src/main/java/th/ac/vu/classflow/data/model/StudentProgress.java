package th.ac.vu.classflow.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StudentProgress {
    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_ON_TRACK = "ON_TRACK";
    public static final String STATUS_NEEDS_ATTENTION = "NEEDS_ATTENTION";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    public static final String METRIC_NOT_STARTED = "NOT_STARTED";
    public static final String METRIC_IN_PROGRESS = "IN_PROGRESS";
    public static final String METRIC_DONE = "DONE";
    public static final String METRIC_NEEDS_PRACTICE = "NEEDS_PRACTICE";
    public static final String METRIC_GOOD = "GOOD";

    private String progressId;
    private String classId;
    private String trackId;
    private String studentId;
    private String weekId;
    private int weekNumber;
    private Map<String, String> metrics = new LinkedHashMap<>();
    private String overallStatus;
    private String blocker;
    private String teacherNote;
    private Timestamp updatedAt;

    public StudentProgress() { }

    public StudentProgress(String classId, String trackId, String studentId,
                           String weekId, int weekNumber, Map<String, String> metrics,
                           String overallStatus, String blocker, String teacherNote) {
        this.classId = classId;
        this.trackId = trackId;
        this.studentId = studentId;
        this.weekId = weekId;
        this.weekNumber = weekNumber;
        this.metrics = new LinkedHashMap<>(metrics);
        this.overallStatus = overallStatus;
        this.blocker = blocker;
        this.teacherNote = teacherNote;
    }

    public static String documentId(String classId, String studentId, String weekId) {
        return classId + "_" + studentId + "_" + weekId;
    }

    public static boolean isValidOverallStatus(String value) {
        return STATUS_NOT_STARTED.equals(value) || STATUS_ON_TRACK.equals(value)
                || STATUS_NEEDS_ATTENTION.equals(value) || STATUS_BLOCKED.equals(value)
                || STATUS_COMPLETED.equals(value);
    }

    public static boolean isValidMetricValue(String value) {
        return METRIC_NOT_STARTED.equals(value) || METRIC_IN_PROGRESS.equals(value)
                || METRIC_DONE.equals(value) || METRIC_NEEDS_PRACTICE.equals(value)
                || METRIC_GOOD.equals(value);
    }

    @Exclude public String getProgressId() { return progressId; }
    public void setProgressId(String progressId) { this.progressId = progressId; }
    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }
    public String getTrackId() { return trackId; }
    public void setTrackId(String trackId) { this.trackId = trackId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getWeekId() { return weekId; }
    public void setWeekId(String weekId) { this.weekId = weekId; }
    public int getWeekNumber() { return weekNumber; }
    public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }
    public Map<String, String> getMetrics() { return metrics; }
    public void setMetrics(Map<String, String> metrics) {
        this.metrics = metrics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metrics);
    }
    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
    public String getBlocker() { return blocker; }
    public void setBlocker(String blocker) { this.blocker = blocker; }
    public String getTeacherNote() { return teacherNote; }
    public void setTeacherNote(String teacherNote) { this.teacherNote = teacherNote; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
