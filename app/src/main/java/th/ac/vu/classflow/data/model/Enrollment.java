package th.ac.vu.classflow.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

public final class Enrollment {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private String enrollmentId;
    private String classId;
    private String trackId;
    private String studentId;
    private String status;
    private Timestamp enrolledAt;
    private java.util.Map<String, Object> assessment;

    public Enrollment() {
        // Required by Firestore.
    }

    public Enrollment(String classId, String trackId, String studentId, String status) {
        this.classId = classId;
        this.trackId = trackId;
        this.studentId = studentId;
        this.status = status;
    }

    public static String documentId(String classId, String studentId) {
        return classId + "_" + studentId;
    }

    @Exclude
    public String getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }
    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }
    public String getTrackId() { return trackId; }
    public void setTrackId(String trackId) { this.trackId = trackId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Timestamp enrolledAt) { this.enrolledAt = enrolledAt; }
    public java.util.Map<String, Object> getAssessment() { return assessment; }
    public void setAssessment(java.util.Map<String, Object> assessment) { this.assessment = assessment; }

    @Exclude
    public AssessmentData getParsedAssessment() {
        return AssessmentData.fromMap(assessment);
    }

    public static boolean isValidStatus(String status) {
        return STATUS_ACTIVE.equals(status) || STATUS_COMPLETED.equals(status)
                || STATUS_WITHDRAWN.equals(status) || STATUS_ARCHIVED.equals(status);
    }
}
