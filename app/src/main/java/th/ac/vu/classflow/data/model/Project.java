package th.ac.vu.classflow.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.List;

public final class Project {
    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_ON_TRACK = "ON_TRACK";
    public static final String STATUS_NEEDS_ATTENTION = "NEEDS_ATTENTION";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private String projectId;
    private String classId;
    private String trackId;
    private String title;
    private String teamName;
    private List<String> memberIds = new ArrayList<>();
    private int currentWeek;
    private String overallStatus = STATUS_NOT_STARTED;
    private boolean active = true;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Project() { }

    public Project(String projectId, String classId, String trackId, String title,
                   String teamName, List<String> memberIds, int currentWeek,
                   String overallStatus, boolean active, Timestamp createdAt, Timestamp updatedAt) {
        this.projectId = projectId;
        this.classId = classId;
        this.trackId = trackId;
        this.title = title;
        this.teamName = teamName;
        if (memberIds != null) {
            this.memberIds = new ArrayList<>(memberIds);
        }
        this.currentWeek = currentWeek;
        this.overallStatus = overallStatus;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static boolean isValidOverallStatus(String value) {
        return STATUS_NOT_STARTED.equals(value) || STATUS_ON_TRACK.equals(value)
                || STATUS_NEEDS_ATTENTION.equals(value) || STATUS_BLOCKED.equals(value)
                || STATUS_COMPLETED.equals(value);
    }

    @Exclude
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getClassId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds != null ? new ArrayList<>(memberIds) : new ArrayList<>();
    }

    public int getCurrentWeek() {
        return currentWeek;
    }

    public void setCurrentWeek(int currentWeek) {
        this.currentWeek = currentWeek;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
