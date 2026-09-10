package th.ac.vu.classflow.data.model;

import com.google.firebase.firestore.Exclude;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Track {

    private String trackId;
    private String code;
    private String name;
    private String description;
    private int totalWeeks;
    private boolean active;

    public Track() {
        // Required by Firestore.
    }

    public Track(String trackId, String code, String name, String description,
                 int totalWeeks, boolean active) {
        this.trackId = trackId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.totalWeeks = totalWeeks;
        this.active = active;
    }

    @Exclude
    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTotalWeeks() {
        return totalWeeks;
    }

    public void setTotalWeeks(int totalWeeks) {
        this.totalWeeks = totalWeeks;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", code);
        values.put("name", name);
        values.put("description", description);
        values.put("totalWeeks", totalWeeks);
        values.put("active", active);
        return values;
    }
}
