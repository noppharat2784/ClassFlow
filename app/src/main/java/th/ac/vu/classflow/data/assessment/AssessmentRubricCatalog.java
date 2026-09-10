package th.ac.vu.classflow.data.assessment;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import th.ac.vu.classflow.data.model.AssessmentData;

public final class AssessmentRubricCatalog {

    public static final String RUBRIC_ID_SS1 = "SS1_COMPETENCY_V1";
    public static final String TRACK_ID_SS1 = "ss1";
    public static final String TRACK_SS1 = "ss1";

    public static final class DomainDef {
        private final int id;
        private final String title;
        private final List<CriterionDef> criteria;

        DomainDef(int id, String title, List<CriterionDef> criteria) {
            this.id = id;
            this.title = title;
            this.criteria = Collections.unmodifiableList(criteria);
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public List<CriterionDef> getCriteria() { return criteria; }
    }

    public static final class CriterionDef {
        private final String id;
        private final String label;
        @Nullable
        private final String observationGuidance;

        CriterionDef(String id, String label, @Nullable String observationGuidance) {
            this.id = id;
            this.label = label;
            this.observationGuidance = observationGuidance;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
        @Nullable
        public String getObservationGuidance() { return observationGuidance; }
    }

    public static final class ValueOption {
        private final Object value; // Integer (1..4) or String ("NOT_OBSERVED")
        private final String shortLabel;
        private final String fullLabel;

        ValueOption(Object value, String shortLabel, String fullLabel) {
            this.value = value;
            this.shortLabel = shortLabel;
            this.fullLabel = fullLabel;
        }

        public Object getValue() { return value; }
        public String getShortLabel() { return shortLabel; }
        public String getFullLabel() { return fullLabel; }
    }

    private static final List<DomainDef> SS1_DOMAINS;
    private static final List<String> ALL_SS1_CRITERIA_KEYS;
    private static final List<ValueOption> VALUE_OPTIONS;
    private static final Map<String, CriterionDef> CRITERIA_MAP;

    static {
        List<DomainDef> domains = new ArrayList<>();

        List<CriterionDef> d1 = Arrays.asList(
                new CriterionDef("1.1", "เลือกใช้ Python Tool / Concept ได้เหมาะสมกับงาน", null),
                new CriterionDef("1.2", "เขียนหรือแก้ Code พื้นฐานได้", null),
                new CriterionDef("1.3", "นำหลาย Concept มาใช้ร่วมกันได้", null)
        );
        domains.add(new DomainDef(1, "Coding Foundation", d1));

        List<CriterionDef> d2 = Arrays.asList(
                new CriterionDef("2.1", "แยกปัญหาออกเป็นส่วนย่อยได้", null),
                new CriterionDef("2.2", "วางลำดับหรือ Flow ของ Program ได้", null),
                new CriterionDef("2.3", "ปรับ Logic เมื่อ Requirement หรือเงื่อนไขเปลี่ยนได้", null)
        );
        domains.add(new DomainDef(2, "Logic & Problem Solving", d2));

        List<CriterionDef> d3 = Arrays.asList(
                new CriterionDef("3.1", "สร้าง Minimum Viable Project / working solution ได้", null),
                new CriterionDef("3.2", "ทดสอบด้วยกรณีที่เหมาะสมได้", null),
                new CriterionDef("3.3", "หาสาเหตุและทดลองแก้ Bug ได้", null)
        );
        domains.add(new DomainDef(3, "Build & Debug", d3));

        List<CriterionDef> d4 = Arrays.asList(
                new CriterionDef("4.1", "อธิบาย Main Program Flow ได้", null),
                new CriterionDef("4.2", "อธิบายความสัมพันธ์ของ Data / Function / Logic ได้", null),
                new CriterionDef("4.3", "ปรับหรือแก้ Code ของตนเองได้", null)
        );
        domains.add(new DomainDef(4, "Code Ownership & Explanation", d4));

        List<CriterionDef> d5 = Arrays.asList(
                new CriterionDef("5.1", "เริ่มทำงานได้ด้วยตนเอง", null),
                new CriterionDef("5.2", "ใช้ Hint แล้วสามารถไปต่อได้", null),
                new CriterionDef("5.3", "รับ Feedback และนำไปปรับปรุงได้", null)
        );
        domains.add(new DomainDef(5, "Independence & Learning Growth", d5));

        SS1_DOMAINS = Collections.unmodifiableList(domains);

        List<String> keys = new ArrayList<>();
        Map<String, CriterionDef> map = new LinkedHashMap<>();
        for (DomainDef d : SS1_DOMAINS) {
            for (CriterionDef c : d.getCriteria()) {
                keys.add(c.getId());
                map.put(c.getId(), c);
            }
        }
        ALL_SS1_CRITERIA_KEYS = Collections.unmodifiableList(keys);
        CRITERIA_MAP = Collections.unmodifiableMap(map);

        VALUE_OPTIONS = Collections.unmodifiableList(Arrays.asList(
                new ValueOption(4, "4", "4 — ทำได้เองและสามารถประยุกต์หรือปรับแก้ได้"),
                new ValueOption(3, "3", "3 — เข้าใจและทำได้เมื่อมี Hint เล็กน้อย"),
                new ValueOption(2, "2", "2 — ทำได้เมื่อมี Guidance เป็นขั้นตอน"),
                new ValueOption(1, "1", "1 — ยังต้องทบทวนพื้นฐาน / ยังทำเองไม่ได้"),
                new ValueOption(AssessmentData.VALUE_NOT_OBSERVED, "N/O", "N/O — ยังไม่มีข้อมูลสังเกตเพียงพอ")
        ));
    }

    private AssessmentRubricCatalog() { }

    public static boolean isSupportedTrack(String trackId) {
        return TRACK_ID_SS1.equalsIgnoreCase(trackId);
    }

    public static List<DomainDef> getDomainsForTrack(String trackId) {
        if (isSupportedTrack(trackId)) {
            return SS1_DOMAINS;
        }
        return Collections.emptyList();
    }

    public static List<String> getApprovedCriteriaKeys() {
        return ALL_SS1_CRITERIA_KEYS;
    }

    public static List<ValueOption> getValueOptions() {
        return VALUE_OPTIONS;
    }

    @Nullable
    public static CriterionDef getCriterion(String criterionId) {
        return CRITERIA_MAP.get(criterionId);
    }

    public static boolean isValidCriterionValue(@Nullable Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number) {
            Number num = (Number) value;
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || Math.floor(d) != d) {
                return false;
            }
            long l = num.longValue();
            return l >= 1 && l <= 4;
        }
        if (value instanceof String) {
            return AssessmentData.VALUE_NOT_OBSERVED.equals(value);
        }
        return false;
    }
}
