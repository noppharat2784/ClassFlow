package th.ac.vu.classflow.ui.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import th.ac.vu.classflow.data.model.StudentProgress;

public final class DashboardItem implements Comparable<DashboardItem> {

    public enum Type {
        STUDENT,
        PROJECT
    }

    private final Type type;
    private final String status; // BLOCKED or NEEDS_ATTENTION
    private final String classId;
    private final String className;
    private final String trackId;
    private final String trackCode;
    private final String trackName;
    private final int weekNumber;
    private final String weekId;
    @Nullable private final String blocker;

    // Student fields
    @Nullable private final String studentId;
    @Nullable private final String studentName;
    @Nullable private final String studentNickname;
    @Nullable private final String teacherNote;

    // Project fields
    @Nullable private final String projectId;
    @Nullable private final String projectTitle;
    @Nullable private final String teamName;

    public static DashboardItem createStudent(@NonNull String status,
                                              @NonNull String classId,
                                              @NonNull String className,
                                              @NonNull String trackId,
                                              @NonNull String trackCode,
                                              @NonNull String trackName,
                                              int weekNumber,
                                              @NonNull String weekId,
                                              @NonNull String studentId,
                                              @NonNull String studentName,
                                              @Nullable String studentNickname,
                                              @Nullable String blocker,
                                              @Nullable String teacherNote) {
        return new DashboardItem(Type.STUDENT, status, classId, className, trackId, trackCode,
                trackName, weekNumber, weekId, blocker, studentId, studentName, studentNickname,
                teacherNote, null, null, null);
    }

    public static DashboardItem createProject(@NonNull String status,
                                              @NonNull String classId,
                                              @NonNull String className,
                                              @NonNull String trackId,
                                              @NonNull String trackCode,
                                              @NonNull String trackName,
                                              int weekNumber,
                                              @NonNull String weekId,
                                              @NonNull String projectId,
                                              @NonNull String projectTitle,
                                              @Nullable String teamName,
                                              @Nullable String blocker) {
        return new DashboardItem(Type.PROJECT, status, classId, className, trackId, trackCode,
                trackName, weekNumber, weekId, blocker, null, null, null, null,
                projectId, projectTitle, teamName);
    }

    private DashboardItem(Type type,
                          String status,
                          String classId,
                          String className,
                          String trackId,
                          String trackCode,
                          String trackName,
                          int weekNumber,
                          String weekId,
                          @Nullable String blocker,
                          @Nullable String studentId,
                          @Nullable String studentName,
                          @Nullable String studentNickname,
                          @Nullable String teacherNote,
                          @Nullable String projectId,
                          @Nullable String projectTitle,
                          @Nullable String teamName) {
        this.type = type;
        this.status = status;
        this.classId = classId;
        this.className = className;
        this.trackId = trackId;
        this.trackCode = trackCode;
        this.trackName = trackName;
        this.weekNumber = weekNumber;
        this.weekId = weekId;
        this.blocker = blocker;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentNickname = studentNickname;
        this.teacherNote = teacherNote;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.teamName = teamName;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    @NonNull
    public String getClassId() {
        return classId;
    }

    @NonNull
    public String getClassName() {
        return className;
    }

    @NonNull
    public String getTrackId() {
        return trackId;
    }

    @NonNull
    public String getTrackCode() {
        return trackCode;
    }

    @NonNull
    public String getTrackName() {
        return trackName;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    @NonNull
    public String getWeekId() {
        return weekId;
    }

    @Nullable
    public String getBlocker() {
        return blocker;
    }

    @Nullable
    public String getStudentId() {
        return studentId;
    }

    @Nullable
    public String getStudentName() {
        return studentName;
    }

    @Nullable
    public String getStudentNickname() {
        return studentNickname;
    }

    @Nullable
    public String getTeacherNote() {
        return teacherNote;
    }

    @Nullable
    public String getProjectId() {
        return projectId;
    }

    @Nullable
    public String getProjectTitle() {
        return projectTitle;
    }

    @Nullable
    public String getTeamName() {
        return teamName;
    }

    @NonNull
    public String getEntityDisplayName() {
        if (type == Type.STUDENT) {
            return studentName != null ? studentName : "";
        } else {
            return projectTitle != null ? projectTitle : "";
        }
    }

    @NonNull
    public String getEntityId() {
        if (type == Type.STUDENT) {
            return studentId != null ? studentId : "";
        } else {
            return projectId != null ? projectId : "";
        }
    }

    @Override
    public int compareTo(@NonNull DashboardItem other) {
        // 1. Primary: BLOCKED before NEEDS_ATTENTION
        int statusComparison = getStatusPriority(this.status) - getStatusPriority(other.status);
        if (statusComparison != 0) {
            return statusComparison;
        }

        // 2. Secondary: Class name (case-insensitive)
        int classComparison = this.className.compareToIgnoreCase(other.className);
        if (classComparison != 0) {
            return classComparison;
        }

        // 3. Tertiary: Entity display name / title (case-insensitive)
        int nameComparison = this.getEntityDisplayName().compareToIgnoreCase(other.getEntityDisplayName());
        if (nameComparison != 0) {
            return nameComparison;
        }

        // 4. Quaternary: Entity ID tie-breaker
        return this.getEntityId().compareTo(other.getEntityId());
    }

    private static int getStatusPriority(String status) {
        if (StudentProgress.STATUS_BLOCKED.equals(status)) {
            return 1;
        }
        if (StudentProgress.STATUS_NEEDS_ATTENTION.equals(status)) {
            return 2;
        }
        return 3;
    }
}
