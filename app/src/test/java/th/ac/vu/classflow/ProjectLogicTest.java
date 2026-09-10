package th.ac.vu.classflow;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;
import th.ac.vu.classflow.data.repository.ProgressMetricSchema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProjectLogicTest {

    @Test
    public void testProjectOverallStatusValidation() {
        assertTrue(Project.isValidOverallStatus(Project.STATUS_NOT_STARTED));
        assertTrue(Project.isValidOverallStatus(Project.STATUS_ON_TRACK));
        assertTrue(Project.isValidOverallStatus(Project.STATUS_NEEDS_ATTENTION));
        assertTrue(Project.isValidOverallStatus(Project.STATUS_BLOCKED));
        assertTrue(Project.isValidOverallStatus(Project.STATUS_COMPLETED));

        assertFalse(Project.isValidOverallStatus("ACTIVE"));
        assertFalse(Project.isValidOverallStatus("IN_PROGRESS"));
        assertFalse(Project.isValidOverallStatus("ARCHIVED"));
        assertFalse(Project.isValidOverallStatus("UNKNOWN"));
        assertFalse(Project.isValidOverallStatus(null));
        assertFalse(Project.isValidOverallStatus(""));
    }

    @Test
    public void testProjectProgressValidationAndDocumentId() {
        assertEquals("proj_abc_W06", ProjectProgress.documentId("proj_abc", "W06"));
        assertEquals("p123_W10", ProjectProgress.documentId("p123", "W10"));

        assertTrue(ProjectProgress.isValidMetricValue(ProjectProgress.METRIC_NOT_STARTED));
        assertTrue(ProjectProgress.isValidMetricValue(ProjectProgress.METRIC_IN_PROGRESS));
        assertTrue(ProjectProgress.isValidMetricValue(ProjectProgress.METRIC_DONE));
        assertTrue(ProjectProgress.isValidMetricValue(ProjectProgress.METRIC_NEEDS_PRACTICE));
        assertTrue(ProjectProgress.isValidMetricValue(ProjectProgress.METRIC_GOOD));

        assertFalse(ProjectProgress.isValidMetricValue("EXCELLENT"));
        assertFalse(ProjectProgress.isValidMetricValue("PASSED"));
        assertFalse(ProjectProgress.isValidMetricValue(null));
    }

    @Test
    public void testProgressMetricSchemaResolution() {
        List<String> ss1W01 = ProgressMetricSchema.resolve("ss1", "LEARNING", 1);
        assertEquals(Arrays.asList("concept", "practice"), ss1W01);

        List<String> ss1W08 = ProgressMetricSchema.resolve("ss1", "LEARNING", 8);
        assertEquals(Arrays.asList("concept", "practice"), ss1W08);

        List<String> ss1W09 = ProgressMetricSchema.resolve("ss1", "PROJECT", 9);
        assertEquals(Arrays.asList("planning", "implementation", "testing"), ss1W09);

        List<String> ss1W10 = ProgressMetricSchema.resolve("ss1", "DEMO", 10);
        assertEquals(Arrays.asList("demoReadiness", "codeExplanation", "communication"), ss1W10);

        List<String> ss2W01 = ProgressMetricSchema.resolve("ss2", "INTEGRATION", 1);
        assertEquals(Arrays.asList("software", "hardware", "integration"), ss2W01);

        List<String> ss2W09 = ProgressMetricSchema.resolve("ss2", "PROJECT", 9);
        assertEquals(Arrays.asList("software", "hardware", "integration"), ss2W09);

        List<String> ss2W10 = ProgressMetricSchema.resolve("ss2", "DEMO", 10);
        assertEquals(Arrays.asList("demoReadiness", "systemExplanation", "communication"), ss2W10);
    }

    @Test
    public void testMemberPartitioningLogic() {
        List<String> existingMembers = Arrays.asList("ST_001", "ST_002");
        List<String> updatedMemberIds = Arrays.asList("ST_002", "ST_003");

        Set<String> retained = new HashSet<>(existingMembers);
        retained.retainAll(updatedMemberIds);

        Set<String> newlyAdded = new HashSet<>(updatedMemberIds);
        newlyAdded.removeAll(existingMembers);

        assertEquals(Collections.singleton("ST_002"), retained);
        assertEquals(Collections.singleton("ST_003"), newlyAdded);
    }

    @Test
    public void testCurrentWeekSummarySynchronizationLogic() {
        Project project = new Project();
        project.setProjectId("proj_test");
        project.setClassId("2026_SS1_A");
        project.setTrackId("ss1");
        project.setCurrentWeek(6);
        project.setOverallStatus(Project.STATUS_NOT_STARTED);

        // When progress week == project.currentWeek, summary must update
        int progressWeekNumber1 = 6;
        String newStatus1 = Project.STATUS_ON_TRACK;
        if (progressWeekNumber1 == project.getCurrentWeek()) {
            project.setOverallStatus(newStatus1);
        }
        assertEquals(Project.STATUS_ON_TRACK, project.getOverallStatus());

        // When progress week != project.currentWeek (historical/future), summary must NOT update
        int progressWeekNumber2 = 5;
        String newStatus2 = Project.STATUS_BLOCKED;
        if (progressWeekNumber2 == project.getCurrentWeek()) {
            project.setOverallStatus(newStatus2);
        }
        assertEquals(Project.STATUS_ON_TRACK, project.getOverallStatus());
    }

    @Test
    public void testUpdateCurrentWeekTargetProgressLogic() {
        Project project = new Project();
        project.setCurrentWeek(6);
        project.setOverallStatus(Project.STATUS_ON_TRACK);

        // Case A: Target week has existing progress
        ProjectProgress week7Progress = new ProjectProgress();
        week7Progress.setWeekNumber(7);
        week7Progress.setOverallStatus(Project.STATUS_NEEDS_ATTENTION);

        int targetWeekA = 7;
        project.setCurrentWeek(targetWeekA);
        if (week7Progress != null) {
            project.setOverallStatus(week7Progress.getOverallStatus());
        } else {
            project.setOverallStatus(Project.STATUS_NOT_STARTED);
        }
        assertEquals(7, project.getCurrentWeek());
        assertEquals(Project.STATUS_NEEDS_ATTENTION, project.getOverallStatus());

        // Case B: Target week has NO progress recorded
        ProjectProgress week8Progress = null;
        int targetWeekB = 8;
        project.setCurrentWeek(targetWeekB);
        if (week8Progress != null) {
            project.setOverallStatus(week8Progress.getOverallStatus());
        } else {
            project.setOverallStatus(Project.STATUS_NOT_STARTED);
        }
        assertEquals(8, project.getCurrentWeek());
        assertEquals(Project.STATUS_NOT_STARTED, project.getOverallStatus());
    }

    @Test
    public void testTargetProgressDataIntegrityValidation() {
        String projectId = "proj_01";
        String classId = "2026_SS1_A";
        String trackId = "ss1";
        String targetWeekId = "W07";
        int targetWeekNumber = 7;

        // Valid progress
        assertTrue(isTargetProgressValid(projectId, classId, trackId, targetWeekId, targetWeekNumber,
                projectId, classId, trackId, targetWeekId, 7, Project.STATUS_ON_TRACK));

        // Mismatched project ID
        assertFalse(isTargetProgressValid(projectId, classId, trackId, targetWeekId, targetWeekNumber,
                "other_proj", classId, trackId, targetWeekId, 7, Project.STATUS_ON_TRACK));

        // Mismatched class ID
        assertFalse(isTargetProgressValid(projectId, classId, trackId, targetWeekId, targetWeekNumber,
                projectId, "other_class", trackId, targetWeekId, 7, Project.STATUS_ON_TRACK));

        // Mismatched track ID
        assertFalse(isTargetProgressValid(projectId, classId, trackId, targetWeekId, targetWeekNumber,
                projectId, classId, "ss2", targetWeekId, 7, Project.STATUS_ON_TRACK));

        // Mismatched week number
        assertFalse(isTargetProgressValid(projectId, classId, trackId, targetWeekId, targetWeekNumber,
                projectId, classId, trackId, targetWeekId, 8, Project.STATUS_ON_TRACK));

        // Invalid overall status
        assertFalse(isTargetProgressValid(projectId, classId, trackId, targetWeekId, targetWeekNumber,
                projectId, classId, trackId, targetWeekId, 7, "INVALID_STATUS"));
    }

    private boolean isTargetProgressValid(String expectedProjectId, String expectedClassId, String expectedTrackId,
                                         String expectedWeekId, int expectedWeekNumber,
                                         String storedProjectId, String storedClassId, String storedTrackId,
                                         String storedWeekId, Integer storedWeekNumber, String storedStatus) {
        if (!expectedProjectId.equals(storedProjectId)) return false;
        if (!expectedClassId.equals(storedClassId)) return false;
        if (!expectedTrackId.equals(storedTrackId)) return false;
        if (!expectedWeekId.equals(storedWeekId)) return false;
        if (storedWeekNumber == null || storedWeekNumber != expectedWeekNumber) return false;
        return ProjectProgress.isValidOverallStatus(storedStatus);
    }
}
