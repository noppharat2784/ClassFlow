package th.ac.vu.classflow.data.model;

import com.google.firebase.firestore.Exclude;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CurriculumWeek {

    private String weekId;
    private int weekNumber;
    private String title;
    private String summary;
    private String phase;
    private String progressType;

    public CurriculumWeek() {
        // Required by Firestore.
    }

    public CurriculumWeek(String weekId, int weekNumber, String title, String summary,
                          String phase, String progressType) {
        this.weekId = weekId;
        this.weekNumber = weekNumber;
        this.title = title;
        this.summary = summary;
        this.phase = phase;
        this.progressType = progressType;
    }

    @Exclude
    public String getWeekId() {
        return weekId;
    }

    public void setWeekId(String weekId) {
        this.weekId = weekId;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public void setWeekNumber(int weekNumber) {
        this.weekNumber = weekNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getProgressType() {
        return progressType;
    }

    public void setProgressType(String progressType) {
        this.progressType = progressType;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("weekNumber", weekNumber);
        values.put("title", title);
        values.put("summary", summary);
        values.put("phase", phase);
        values.put("progressType", progressType);
        return values;
    }
}
