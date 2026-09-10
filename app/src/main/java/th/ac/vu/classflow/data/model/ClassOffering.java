package th.ac.vu.classflow.data.model;

import com.google.firebase.Timestamp;

public final class ClassOffering {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private String classId;
    private String name;
    private String trackId;
    private int currentWeek;
    private String status;
    private Timestamp startDate;
    private Timestamp createdAt;

    public ClassOffering() {
        // Required by Firestore.
    }

    public ClassOffering(String classId, String name, String trackId, int currentWeek,
                         String status, Timestamp startDate) {
        this.classId = classId;
        this.name = name;
        this.trackId = trackId;
        this.currentWeek = currentWeek;
        this.status = status;
        this.startDate = startDate;
    }

    public String getClassId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public int getCurrentWeek() {
        return currentWeek;
    }

    public void setCurrentWeek(int currentWeek) {
        this.currentWeek = currentWeek;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public static boolean isValidStatus(String status) {
        return STATUS_PLANNED.equals(status)
                || STATUS_ACTIVE.equals(status)
                || STATUS_COMPLETED.equals(status)
                || STATUS_ARCHIVED.equals(status);
    }
}
