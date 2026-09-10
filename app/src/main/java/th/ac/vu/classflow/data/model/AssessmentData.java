package th.ac.vu.classflow.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AssessmentData {

    public static final String VALUE_NOT_OBSERVED = "NOT_OBSERVED";

    private String rubricId;
    private Map<String, Object> criteria = new LinkedHashMap<>();
    private String strength = "";
    private String nextStep = "";
    @Nullable
    private Timestamp updatedAt;

    public AssessmentData() {
        // Required by Firestore or empty initialization
    }

    public AssessmentData(String rubricId, Map<String, Object> criteria,
                          String strength, String nextStep,
                          @Nullable Timestamp updatedAt) {
        this.rubricId = rubricId;
        this.criteria = criteria != null ? new LinkedHashMap<>(criteria) : new LinkedHashMap<>();
        this.strength = strength != null ? strength : "";
        this.nextStep = nextStep != null ? nextStep : "";
        this.updatedAt = updatedAt;
    }

    public static AssessmentData fromMap(@Nullable Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        String rubricId = (String) map.get("rubricId");
        String strength = (String) map.get("strength");
        String nextStep = (String) map.get("nextStep");
        Timestamp updatedAt = (Timestamp) map.get("updatedAt");

        Map<String, Object> parsedCriteria = new LinkedHashMap<>();
        Object rawCriteria = map.get("criteria");
        if (rawCriteria instanceof Map<?, ?>) {
            Map<?, ?> rawMap = (Map<?, ?>) rawCriteria;
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object val = entry.getValue();
                if (val instanceof Number) {
                    Number num = (Number) val;
                    double d = num.doubleValue();
                    if (!Double.isNaN(d) && !Double.isInfinite(d) && Math.floor(d) == d && d >= 1.0 && d <= 4.0) {
                        parsedCriteria.put(key, (int) d);
                    } else {
                        parsedCriteria.put(key, val);
                    }
                } else if (val instanceof String) {
                    String strVal = (String) val;
                    if (VALUE_NOT_OBSERVED.equals(strVal)) {
                        parsedCriteria.put(key, VALUE_NOT_OBSERVED);
                    } else {
                        parsedCriteria.put(key, strVal);
                    }
                } else if (val != null) {
                    parsedCriteria.put(key, val);
                }
            }
        }

        return new AssessmentData(rubricId, parsedCriteria, strength, nextStep, updatedAt);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rubricId", rubricId);
        map.put("criteria", new LinkedHashMap<>(criteria));
        map.put("strength", strength != null ? strength : "");
        map.put("nextStep", nextStep != null ? nextStep : "");
        if (updatedAt != null) {
            map.put("updatedAt", updatedAt);
        }
        return map;
    }

    public String getRubricId() {
        return rubricId;
    }

    public void setRubricId(String rubricId) {
        this.rubricId = rubricId;
    }

    public Map<String, Object> getCriteria() {
        return criteria;
    }

    public void setCriteria(Map<String, Object> criteria) {
        this.criteria = criteria != null ? new LinkedHashMap<>(criteria) : new LinkedHashMap<>();
    }

    @NonNull
    public String getStrength() {
        return strength != null ? strength : "";
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    @NonNull
    public String getNextStep() {
        return nextStep != null ? nextStep : "";
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }

    @Nullable
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(@Nullable Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
