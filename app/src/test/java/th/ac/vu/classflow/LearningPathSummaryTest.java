package th.ac.vu.classflow;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.ui.model.EnrollmentCardItem;
import th.ac.vu.classflow.ui.model.LearningPathSummary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LearningPathSummaryTest {

    private LearningPathSummary.StringResolver mockResolver;

    @Before
    public void setUp() {
        mockResolver = new LearningPathSummary.StringResolver() {
            @Override
            public String getString(int resId) {
                if (resId == R.string.completed) return "Completed";
                if (resId == R.string.active) return "Active";
                if (resId == R.string.learning_path_multiple_suffix) return " · Multiple records on file";
                return "res_" + resId;
            }

            @Override
            public String getString(int resId, Object... formatArgs) {
                if (resId == R.string.learning_path_case_a) {
                    return String.format("%s (%s) → %s (%s)", formatArgs);
                }
                if (resId == R.string.learning_path_case_b) {
                    return String.format("%s (%s) — eligible / awaiting next track", formatArgs);
                }
                if (resId == R.string.learning_path_case_c) {
                    return String.format("%s (%s)", formatArgs);
                }
                if (resId == R.string.learning_path_case_d) {
                    return String.format("%s (%s) — no SS1 record on file", formatArgs);
                }
                return "res_" + resId;
            }
        };
    }

    private EnrollmentCardItem makeItem(String enrollmentId, String classId, String className,
                                         String trackId, String trackCode, String status) {
        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollmentId(enrollmentId);
        enrollment.setClassId(classId);
        enrollment.setTrackId(trackId);
        enrollment.setStatus(status);

        ClassOffering classOffering = new ClassOffering();
        classOffering.setClassId(classId);
        classOffering.setName(className);
        classOffering.setTrackId(trackId);

        Track track = new Track();
        track.setTrackId(trackId);
        track.setCode(trackCode);
        track.setName(trackCode + " Track");

        return new EnrollmentCardItem(enrollment, classOffering, track);
    }

    @Test
    public void testNullOrEmptyList_NotVisible() {
        LearningPathSummary summaryNull = LearningPathSummary.from(mockResolver, null);
        assertFalse(summaryNull.isVisible());
        assertNull(summaryNull.getSummaryText());

        LearningPathSummary summaryEmpty = LearningPathSummary.from(mockResolver, Collections.emptyList());
        assertFalse(summaryEmpty.isVisible());
        assertNull(summaryEmpty.getSummaryText());
    }

    @Test
    public void testOtherTracksOnly_NotVisible() {
        EnrollmentCardItem wdItem = makeItem("EN_01", "CLS_WD", "Web Dev 101", "track_wd", "WD1", Enrollment.STATUS_ACTIVE);
        LearningPathSummary summary = LearningPathSummary.from(mockResolver, Collections.singletonList(wdItem));
        assertFalse(summary.isVisible());
        assertNull(summary.getSummaryText());
    }

    @Test
    public void testCaseA_Ss1CompletedAndSs2Active() {
        EnrollmentCardItem ss1 = makeItem("EN_SS1", "CLS_SS1", "Spring 2025 SS1", "track_ss1", "SS1", Enrollment.STATUS_COMPLETED);
        EnrollmentCardItem ss2 = makeItem("EN_SS2", "CLS_SS2", "Fall 2025 SS2", "track_ss2", "SS2", Enrollment.STATUS_ACTIVE);

        LearningPathSummary summary = LearningPathSummary.from(mockResolver, Arrays.asList(ss1, ss2));
        assertTrue(summary.isVisible());
        assertNotNull(summary.getSummaryText());
        assertEquals("Spring 2025 SS1 (Completed) → Fall 2025 SS2 (Active)", summary.getSummaryText());
    }

    @Test
    public void testCaseB_Ss1CompletedNoSs2() {
        EnrollmentCardItem ss1 = makeItem("EN_SS1", "CLS_SS1", "Spring 2025 SS1", "track_ss1", "SS1", Enrollment.STATUS_COMPLETED);

        LearningPathSummary summary = LearningPathSummary.from(mockResolver, Collections.singletonList(ss1));
        assertTrue(summary.isVisible());
        assertNotNull(summary.getSummaryText());
        assertEquals("Spring 2025 SS1 (Completed) — eligible / awaiting next track", summary.getSummaryText());
    }

    @Test
    public void testCaseC_Ss1ActiveNoSs2() {
        EnrollmentCardItem ss1 = makeItem("EN_SS1", "CLS_SS1", "Summer 2026 SS1-A", "track_ss1", "SS1", Enrollment.STATUS_ACTIVE);

        LearningPathSummary summary = LearningPathSummary.from(mockResolver, Collections.singletonList(ss1));
        assertTrue(summary.isVisible());
        assertNotNull(summary.getSummaryText());
        assertEquals("Summer 2026 SS1-A (Active)", summary.getSummaryText());
    }

    @Test
    public void testCaseD_Ss2ActiveNoSs1() {
        EnrollmentCardItem ss2 = makeItem("EN_SS2", "CLS_SS2", "Fall 2026 SS2-B", "track_ss2", "SS2", Enrollment.STATUS_ACTIVE);

        LearningPathSummary summary = LearningPathSummary.from(mockResolver, Collections.singletonList(ss2));
        assertTrue(summary.isVisible());
        assertNotNull(summary.getSummaryText());
        assertEquals("Fall 2026 SS2-B (Active) — no SS1 record on file", summary.getSummaryText());
    }

    @Test
    public void testMultipleRecordsSuffix() {
        EnrollmentCardItem ss1A = makeItem("EN_SS1_A", "CLS_SS1_A", "Spring 2024 SS1", "track_ss1", "SS1", Enrollment.STATUS_COMPLETED);
        EnrollmentCardItem ss1B = makeItem("EN_SS1_B", "CLS_SS1_B", "Fall 2024 SS1 Repeat", "track_ss1", "SS1", Enrollment.STATUS_COMPLETED);
        EnrollmentCardItem ss2 = makeItem("EN_SS2", "CLS_SS2", "Spring 2025 SS2", "track_ss2", "SS2", Enrollment.STATUS_ACTIVE);

        LearningPathSummary summary = LearningPathSummary.from(mockResolver, Arrays.asList(ss1A, ss1B, ss2));
        assertTrue(summary.isVisible());
        assertNotNull(summary.getSummaryText());
        assertTrue("Must include multiple records suffix",
                summary.getSummaryText().endsWith(" · Multiple records on file"));
        assertTrue("Must describe progression",
                summary.getSummaryText().contains("Spring 2024 SS1 (Completed) → Spring 2025 SS2 (Active)"));
    }

    @Test
    public void testGenericMultiTrackSummary() {
        // Both SS1 and SS2 completed
        EnrollmentCardItem ss1 = makeItem("EN_SS1", "CLS_SS1", "Spring 2025 SS1", "track_ss1", "SS1", Enrollment.STATUS_COMPLETED);
        EnrollmentCardItem ss2 = makeItem("EN_SS2", "CLS_SS2", "Fall 2025 SS2", "track_ss2", "SS2", Enrollment.STATUS_COMPLETED);

        LearningPathSummary summary = LearningPathSummary.from(mockResolver, Arrays.asList(ss1, ss2));
        assertTrue(summary.isVisible());
        assertNotNull(summary.getSummaryText());
        assertEquals("Spring 2025 SS1 (SS1) [COMPLETED] → Fall 2025 SS2 (SS2) [COMPLETED]", summary.getSummaryText());
    }
}
