package th.ac.vu.classflow;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.ui.model.DashboardClassSummary;
import th.ac.vu.classflow.ui.model.DashboardDerivationHelper;
import th.ac.vu.classflow.ui.model.DashboardItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DashboardDerivationTest {

    private ClassOffering createClass(String classId, String name, String trackId, int currentWeek, String status) {
        ClassOffering c = new ClassOffering();
        c.setClassId(classId);
        c.setName(name);
        c.setTrackId(trackId);
        c.setCurrentWeek(currentWeek);
        c.setStatus(status);
        return c;
    }

    private Track createTrack(String trackId, String code, String name) {
        Track t = new Track();
        t.setTrackId(trackId);
        t.setCode(code);
        t.setName(name);
        return t;
    }

    private CurriculumWeek createWeek(int weekNumber, String title) {
        CurriculumWeek w = new CurriculumWeek();
        w.setWeekNumber(weekNumber);
        w.setTitle(title);
        return w;
    }

    private Enrollment createEnrollment(String classId, String studentId, String status) {
        Enrollment e = new Enrollment();
        e.setEnrollmentId(Enrollment.documentId(classId, studentId));
        e.setClassId(classId);
        e.setStudentId(studentId);
        e.setStatus(status);
        return e;
    }

    private Student createStudent(String studentId, String name, String nickname) {
        Student s = new Student();
        s.setStudentId(studentId);
        s.setName(name);
        s.setNickname(nickname);
        s.setActive(true);
        return s;
    }

    private StudentProgress createStudentProgress(String classId, String trackId, int weekNumber,
                                                 String weekId, String studentId, String overallStatus,
                                                 String blocker, String teacherNote) {
        StudentProgress sp = new StudentProgress();
        sp.setProgressId(classId + "_" + weekId + "_" + studentId);
        sp.setClassId(classId);
        sp.setTrackId(trackId);
        sp.setWeekNumber(weekNumber);
        sp.setWeekId(weekId);
        sp.setStudentId(studentId);
        sp.setOverallStatus(overallStatus);
        sp.setBlocker(blocker);
        sp.setTeacherNote(teacherNote);
        return sp;
    }

    private Project createProject(String projectId, String classId, String trackId, String title,
                                  String teamName, int currentWeek, String overallStatus, boolean active) {
        Project p = new Project();
        p.setProjectId(projectId);
        p.setClassId(classId);
        p.setTrackId(trackId);
        p.setTitle(title);
        p.setTeamName(teamName);
        p.setCurrentWeek(currentWeek);
        p.setOverallStatus(overallStatus);
        p.setActive(active);
        return p;
    }

    @Test
    public void testUrgencyOrderingBlockedBeforeNeedsAttention() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        List<Enrollment> enrollments = Arrays.asList(
                createEnrollment("class_a", "st_1", Enrollment.STATUS_ACTIVE),
                createEnrollment("class_a", "st_2", Enrollment.STATUS_ACTIVE)
        );

        List<StudentProgress> progresses = Arrays.asList(
                createStudentProgress("class_a", "ss1", 5, "W05", "st_1", StudentProgress.STATUS_NEEDS_ATTENTION, null, "Needs review"),
                createStudentProgress("class_a", "ss1", 5, "W05", "st_2", StudentProgress.STATUS_BLOCKED, "Stuck on Git", null)
        );

        Map<String, Student> students = new HashMap<>();
        students.put("st_1", createStudent("st_1", "Bob Smith", "Bob"));
        students.put("st_2", createStudent("st_2", "Alice Cooper", "Alice"));

        // Add projects: 1 BLOCKED, 1 NEEDS_ATTENTION
        Project proj1 = createProject("proj_1", "class_a", "ss1", "Zeta App", "Team Zeta", 5, Project.STATUS_BLOCKED, true);
        Project proj2 = createProject("proj_2", "class_a", "ss1", "Alpha Web", "Team Alpha", 5, Project.STATUS_NEEDS_ATTENTION, true);

        Map<String, Track> tracks = Collections.singletonMap("ss1", track);
        Map<String, CurriculumWeek> weeks = Collections.singletonMap("class_a", week5);
        Map<String, List<Enrollment>> enrMap = Collections.singletonMap("class_a", enrollments);
        Map<String, List<StudentProgress>> progMap = Collections.singletonMap("class_a", progresses);

        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                tracks,
                weeks,
                enrMap,
                progMap,
                students,
                Arrays.asList(proj1, proj2),
                Collections.emptyMap(),
                null
        );

        List<DashboardItem> items = result.getInterventionItems();
        assertEquals(4, items.size());

        // First two must be BLOCKED
        assertEquals(StudentProgress.STATUS_BLOCKED, items.get(0).getStatus());
        assertEquals(StudentProgress.STATUS_BLOCKED, items.get(1).getStatus());

        // Within BLOCKED: Class name is same ("Class Alpha").
        // Entity name: "Alice Cooper" vs "Zeta App" -> "Alice Cooper" comes first
        assertEquals("st_2", items.get(0).getStudentId());
        assertEquals("Alice Cooper", items.get(0).getStudentName());
        assertEquals("proj_1", items.get(1).getProjectId());
        assertEquals("Zeta App", items.get(1).getProjectTitle());

        // Next two must be NEEDS_ATTENTION
        assertEquals(StudentProgress.STATUS_NEEDS_ATTENTION, items.get(2).getStatus());
        assertEquals(StudentProgress.STATUS_NEEDS_ATTENTION, items.get(3).getStatus());

        // Within NEEDS_ATTENTION: "Alpha Web" vs "Bob Smith" -> "Alpha Web" comes first
        assertEquals("proj_2", items.get(2).getProjectId());
        assertEquals("Alpha Web", items.get(2).getProjectTitle());
        assertEquals("st_1", items.get(3).getStudentId());
        assertEquals("Bob Smith", items.get(3).getStudentName());
    }

    @Test
    public void testActiveEnrollmentScoping() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        // st_active is ACTIVE; st_completed is COMPLETED; st_ghost has no enrollment in class_a
        List<Enrollment> enrollments = Arrays.asList(
                createEnrollment("class_a", "st_active", Enrollment.STATUS_ACTIVE),
                createEnrollment("class_a", "st_completed", Enrollment.STATUS_COMPLETED)
        );

        List<StudentProgress> progresses = Arrays.asList(
                createStudentProgress("class_a", "ss1", 5, "W05", "st_active", StudentProgress.STATUS_BLOCKED, "Active Blocker", null),
                createStudentProgress("class_a", "ss1", 5, "W05", "st_completed", StudentProgress.STATUS_BLOCKED, "Inactive Blocker", null),
                createStudentProgress("class_a", "ss1", 5, "W05", "st_ghost", StudentProgress.STATUS_BLOCKED, "Ghost Blocker", null)
        );

        Map<String, Student> students = new HashMap<>();
        students.put("st_active", createStudent("st_active", "Active Student", "Act"));
        students.put("st_completed", createStudent("st_completed", "Completed Student", "Done"));
        students.put("st_ghost", createStudent("st_ghost", "Ghost Student", "Ghost"));

        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                Collections.singletonMap("ss1", track),
                Collections.singletonMap("class_a", week5),
                Collections.singletonMap("class_a", enrollments),
                Collections.singletonMap("class_a", progresses),
                students,
                Collections.emptyList(),
                Collections.emptyMap(),
                null
        );

        assertEquals(1, result.getClassSummaries().size());
        DashboardClassSummary summary = result.getClassSummaries().get(0);
        assertEquals(1, summary.getActiveEnrollmentCount());
        assertEquals(1, summary.getBlockedCount());
        assertEquals(0, summary.getNeedsAttentionCount());

        // Only st_active must appear in the intervention items
        assertEquals(1, result.getInterventionItems().size());
        assertEquals("st_active", result.getInterventionItems().get(0).getStudentId());
    }

    @Test
    public void testExclusionOfHealthyAndNotRecordedStates() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        List<Enrollment> enrollments = Arrays.asList(
                createEnrollment("class_a", "st_on_track", Enrollment.STATUS_ACTIVE),
                createEnrollment("class_a", "st_done", Enrollment.STATUS_ACTIVE),
                createEnrollment("class_a", "st_missing", Enrollment.STATUS_ACTIVE) // Missing progress = NOT_RECORDED
        );

        List<StudentProgress> progresses = Arrays.asList(
                createStudentProgress("class_a", "ss1", 5, "W05", "st_on_track", StudentProgress.STATUS_ON_TRACK, null, null),
                createStudentProgress("class_a", "ss1", 5, "W05", "st_done", StudentProgress.STATUS_COMPLETED, null, null)
        );

        Project healthyProj = createProject("proj_1", "class_a", "ss1", "On Track App", "Team 1", 5, Project.STATUS_ON_TRACK, true);
        Project completedProj = createProject("proj_2", "class_a", "ss1", "Done App", "Team 2", 5, Project.STATUS_COMPLETED, true);

        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                Collections.singletonMap("ss1", track),
                Collections.singletonMap("class_a", week5),
                Collections.singletonMap("class_a", enrollments),
                Collections.singletonMap("class_a", progresses),
                Collections.emptyMap(),
                Arrays.asList(healthyProj, completedProj),
                Collections.emptyMap(),
                null
        );

        DashboardClassSummary summary = result.getClassSummaries().get(0);
        assertEquals(3, summary.getActiveEnrollmentCount());
        assertEquals(0, summary.getBlockedCount());
        assertEquals(0, summary.getNeedsAttentionCount());

        // Zero items in intervention feed
        assertTrue(result.getInterventionItems().isEmpty());
    }

    @Test
    public void testBlockedProjectWithMissingOrMalformedProgress() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        Project projBlocked = createProject("proj_blocked", "class_a", "ss1", "Blocked Project", "Team Block", 5, Project.STATUS_BLOCKED, true);

        // Progress map is empty (or progress does not exist)
        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                Collections.singletonMap("ss1", track),
                Collections.singletonMap("class_a", week5),
                Collections.singletonMap("class_a", Collections.emptyList()),
                Collections.singletonMap("class_a", Collections.emptyList()),
                Collections.emptyMap(),
                Collections.singletonList(projBlocked),
                Collections.emptyMap(),
                null
        );

        assertEquals(1, result.getInterventionItems().size());
        DashboardItem item = result.getInterventionItems().get(0);
        assertEquals(DashboardItem.Type.PROJECT, item.getType());
        assertEquals(StudentProgress.STATUS_BLOCKED, item.getStatus());
        assertEquals("proj_blocked", item.getProjectId());
        assertNull(item.getBlocker()); // UI will render "Blocker details unavailable"
    }

    @Test
    public void testPartialFailureMarking() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        Set<String> failures = new HashSet<>();
        failures.add("class_a");

        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                Collections.singletonMap("ss1", track),
                Collections.singletonMap("class_a", week5),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyMap(),
                failures
        );

        assertEquals(1, result.getClassSummaries().size());
        assertTrue(result.getClassSummaries().get(0).isPartiallyUnavailable());
    }

    @Test
    public void testEmptyActiveClasses() {
        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyMap(),
                null
        );

        assertTrue(result.getClassSummaries().isEmpty());
        assertTrue(result.getInterventionItems().isEmpty());
    }

    @Test
    public void testProjectReadFailureMarksPartialUnavailable() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        // When project read fails, projectReadFailed is true
        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                Collections.singletonMap("ss1", track),
                Collections.singletonMap("class_a", week5),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptySet(),
                true // projectReadFailed = true
        );

        assertTrue(result.hasPartialFailure());
        assertEquals(1, result.getClassSummaries().size());
        assertTrue(result.getClassSummaries().get(0).isPartiallyUnavailable());
    }

    @Test
    public void testStudentDetailReadFailurePreservesInterventionAndMarksPartialUnavailable() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        List<Enrollment> enrollments = Collections.singletonList(
                createEnrollment("class_a", "st_1", Enrollment.STATUS_ACTIVE)
        );

        List<StudentProgress> progresses = Collections.singletonList(
                createStudentProgress("class_a", "ss1", 5, "W05", "st_1", StudentProgress.STATUS_BLOCKED, "Stuck", null)
        );

        // studentsById is EMPTY (simulating student read failure)
        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                Collections.singletonMap("ss1", track),
                Collections.singletonMap("class_a", week5),
                Collections.singletonMap("class_a", enrollments),
                Collections.singletonMap("class_a", progresses),
                Collections.emptyMap(), // student read failed
                Collections.emptyList(),
                Collections.emptyMap(),
                null
        );

        // Attention count and condition MUST NOT disappear
        assertEquals(1, result.getClassSummaries().size());
        DashboardClassSummary summary = result.getClassSummaries().get(0);
        assertEquals(1, summary.getBlockedCount());
        assertTrue(summary.isPartiallyUnavailable()); // Marked partially unavailable

        // Item preserved in feed with fallback identity
        assertEquals(1, result.getInterventionItems().size());
        DashboardItem item = result.getInterventionItems().get(0);
        assertEquals("st_1", item.getStudentId());
        assertEquals("st_1", item.getStudentName());
        assertEquals("Student details unavailable", item.getStudentNickname());
        assertTrue(result.hasPartialFailure());
    }

    @Test
    public void testMalformedStudentProgressMarksPartialUnavailable() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        List<Enrollment> enrollments = Collections.singletonList(
                createEnrollment("class_a", "st_1", Enrollment.STATUS_ACTIVE)
        );

        // Progress has trackId mismatch ("ss2" instead of "ss1")
        List<StudentProgress> progresses = Collections.singletonList(
                createStudentProgress("class_a", "ss2", 5, "W05", "st_1", StudentProgress.STATUS_BLOCKED, "Stuck", null)
        );

        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                Collections.singletonMap("ss1", track),
                Collections.singletonMap("class_a", week5),
                Collections.singletonMap("class_a", enrollments),
                Collections.singletonMap("class_a", progresses),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyMap(),
                null
        );

        // Malformed record does NOT create false healthy state without warning
        assertEquals(1, result.getClassSummaries().size());
        assertTrue(result.getClassSummaries().get(0).isPartiallyUnavailable());
        assertTrue(result.hasPartialFailure());
    }

    @Test
    public void testMalformedProjectMarksPartialUnavailable() {
        ClassOffering classA = createClass("class_a", "Class Alpha", "ss1", 5, ClassOffering.STATUS_ACTIVE);
        Track track = createTrack("ss1", "SS1", "Software Studio 1");
        CurriculumWeek week5 = createWeek(5, "Week 5");

        // Project belongs to class_a ("ss1"), but has trackId "ss2"
        Project malformedProj = createProject("proj_mismatch", "class_a", "ss2", "Wrong Track App", "Team X", 5, Project.STATUS_BLOCKED, true);

        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                Collections.singletonList(classA),
                Collections.singletonMap("ss1", track),
                Collections.singletonMap("class_a", week5),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.singletonList(malformedProj),
                Collections.emptyMap(),
                null
        );

        // Malformed project does NOT silently disappear into clean healthy state
        assertEquals(1, result.getClassSummaries().size());
        assertTrue(result.getClassSummaries().get(0).isPartiallyUnavailable());
        assertTrue(result.hasPartialFailure());
    }
}
