package th.ac.vu.classflow.ui.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import th.ac.vu.classflow.data.bootstrap.CurriculumCatalog;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.data.model.Track;

public final class DashboardDerivationHelper {

    private DashboardDerivationHelper() {}

    public static final class DerivationResult {
        private final List<DashboardClassSummary> classSummaries;
        private final List<DashboardItem> interventionItems;
        private final boolean globalPartialFailure;

        public DerivationResult(@NonNull List<DashboardClassSummary> classSummaries,
                                @NonNull List<DashboardItem> interventionItems) {
            this(classSummaries, interventionItems, false);
        }

        public DerivationResult(@NonNull List<DashboardClassSummary> classSummaries,
                                @NonNull List<DashboardItem> interventionItems,
                                boolean globalPartialFailure) {
            this.classSummaries = classSummaries;
            this.interventionItems = interventionItems;
            this.globalPartialFailure = globalPartialFailure;
        }

        @NonNull
        public List<DashboardClassSummary> getClassSummaries() {
            return classSummaries;
        }

        @NonNull
        public List<DashboardItem> getInterventionItems() {
            return interventionItems;
        }

        public boolean hasPartialFailure() {
            if (globalPartialFailure) {
                return true;
            }
            for (DashboardClassSummary summary : classSummaries) {
                if (summary.isPartiallyUnavailable()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static DerivationResult derive(
            @Nullable List<ClassOffering> activeClasses,
            @Nullable Map<String, Track> tracksByTrackId,
            @Nullable Map<String, CurriculumWeek> currentWeeksByClassId,
            @Nullable Map<String, List<Enrollment>> activeEnrollmentsByClassId,
            @Nullable Map<String, List<StudentProgress>> currentProgressByClassId,
            @Nullable Map<String, Student> studentsById,
            @Nullable List<Project> activeProjects,
            @Nullable Map<String, ProjectProgress> currentProjectProgressByProjectId,
            @Nullable Set<String> classesWithReadFailures) {
        return derive(activeClasses, tracksByTrackId, currentWeeksByClassId,
                activeEnrollmentsByClassId, currentProgressByClassId, studentsById,
                activeProjects, currentProjectProgressByProjectId, classesWithReadFailures, false);
    }

    public static DerivationResult derive(
            @Nullable List<ClassOffering> activeClasses,
            @Nullable Map<String, Track> tracksByTrackId,
            @Nullable Map<String, CurriculumWeek> currentWeeksByClassId,
            @Nullable Map<String, List<Enrollment>> activeEnrollmentsByClassId,
            @Nullable Map<String, List<StudentProgress>> currentProgressByClassId,
            @Nullable Map<String, Student> studentsById,
            @Nullable List<Project> activeProjects,
            @Nullable Map<String, ProjectProgress> currentProjectProgressByProjectId,
            @Nullable Set<String> classesWithReadFailures,
            boolean projectReadFailed) {

        List<DashboardClassSummary> summaries = new ArrayList<>();
        List<DashboardItem> items = new ArrayList<>();

        if (activeClasses == null || activeClasses.isEmpty()) {
            return new DerivationResult(summaries, items, projectReadFailed);
        }

        Set<String> activeClassIds = new HashSet<>();
        for (ClassOffering c : activeClasses) {
            if (c != null && ClassOffering.STATUS_ACTIVE.equals(c.getStatus())) {
                activeClassIds.add(c.getClassId());
            }
        }

        // Pre-check active projects for malformed data
        Set<String> classesWithProjectMismatches = new HashSet<>();
        if (activeProjects != null) {
            for (Project p : activeProjects) {
                if (p == null || !p.isActive()) continue;
                String pStatus = p.getOverallStatus();
                boolean isUrgent = StudentProgress.STATUS_BLOCKED.equals(pStatus)
                        || StudentProgress.STATUS_NEEDS_ATTENTION.equals(pStatus);

                if (!activeClassIds.contains(p.getClassId())) {
                    continue;
                }

                ClassOffering pClass = null;
                for (ClassOffering c : activeClasses) {
                    if (c != null && c.getClassId().equals(p.getClassId())) {
                        pClass = c;
                        break;
                    }
                }

                boolean trackMatches = pClass != null && p.getTrackId() != null
                        && p.getTrackId().equals(pClass.getTrackId());
                boolean weekValid = p.getCurrentWeek() >= 1 && p.getCurrentWeek() <= 10;

                if (!trackMatches || !weekValid) {
                    if (isUrgent) {
                        classesWithProjectMismatches.add(p.getClassId());
                    }
                }
            }
        }

        // 1. Process each Active Class
        for (ClassOffering classOffering : activeClasses) {
            if (classOffering == null || !ClassOffering.STATUS_ACTIVE.equals(classOffering.getStatus())) {
                continue;
            }
            String classId = classOffering.getClassId();
            Track track = tracksByTrackId != null ? tracksByTrackId.get(classOffering.getTrackId()) : null;
            CurriculumWeek currentWeek = currentWeeksByClassId != null ? currentWeeksByClassId.get(classId) : null;

            boolean hasReadFailure = (classesWithReadFailures != null && classesWithReadFailures.contains(classId))
                    || classesWithProjectMismatches.contains(classId)
                    || projectReadFailed;
            boolean integrityMismatch = false;

            if (track == null || currentWeek == null) {
                hasReadFailure = true;
            }

            List<Enrollment> activeEnrollments = activeEnrollmentsByClassId != null
                    ? activeEnrollmentsByClassId.get(classId) : null;
            if (activeEnrollments == null) activeEnrollments = Collections.emptyList();

            Set<String> activeStudentIds = new HashSet<>();
            for (Enrollment e : activeEnrollments) {
                if (e != null && Enrollment.STATUS_ACTIVE.equals(e.getStatus())
                        && classId.equals(e.getClassId())) {
                    activeStudentIds.add(e.getStudentId());
                }
            }

            int blockedCount = 0;
            int needsAttentionCount = 0;

            List<StudentProgress> progressList = currentProgressByClassId != null
                    ? currentProgressByClassId.get(classId) : null;
            if (progressList != null && currentWeek != null) {
                String expectedWeekId = CurriculumCatalog.weekId(classOffering.getCurrentWeek());
                for (StudentProgress sp : progressList) {
                    if (sp == null) continue;

                    // Validate cross-domain consistency
                    boolean valid = classId.equals(sp.getClassId())
                            && classOffering.getTrackId() != null
                            && classOffering.getTrackId().equals(sp.getTrackId())
                            && sp.getWeekNumber() == classOffering.getCurrentWeek()
                            && expectedWeekId.equals(sp.getWeekId())
                            && sp.getStudentId() != null
                            && !sp.getStudentId().trim().isEmpty();

                    if (!valid) {
                        String status = sp.getOverallStatus();
                        if (StudentProgress.STATUS_BLOCKED.equals(status)
                                || StudentProgress.STATUS_NEEDS_ATTENTION.equals(status)) {
                            integrityMismatch = true;
                        }
                        continue;
                    }

                    // Scope: Progress contributes ONLY when student belongs to ACTIVE enrollments
                    if (!activeStudentIds.contains(sp.getStudentId())) {
                        continue;
                    }

                    String status = sp.getOverallStatus();
                    if (StudentProgress.STATUS_BLOCKED.equals(status)) {
                        blockedCount++;
                        Student student = studentsById != null ? studentsById.get(sp.getStudentId()) : null;
                        String studentName = student != null ? student.getName() : sp.getStudentId();
                        String studentNickname = student != null ? student.getNickname() : "Student details unavailable";
                        if (student == null) {
                            integrityMismatch = true;
                        }

                        items.add(DashboardItem.createStudent(
                                StudentProgress.STATUS_BLOCKED,
                                classId,
                                classOffering.getName(),
                                track != null ? track.getTrackId() : classOffering.getTrackId(),
                                track != null ? track.getCode() : "",
                                track != null ? track.getName() : "",
                                classOffering.getCurrentWeek(),
                                expectedWeekId,
                                sp.getStudentId(),
                                studentName,
                                studentNickname,
                                sp.getBlocker(),
                                sp.getTeacherNote()
                        ));
                    } else if (StudentProgress.STATUS_NEEDS_ATTENTION.equals(status)) {
                        needsAttentionCount++;
                        Student student = studentsById != null ? studentsById.get(sp.getStudentId()) : null;
                        String studentName = student != null ? student.getName() : sp.getStudentId();
                        String studentNickname = student != null ? student.getNickname() : "Student details unavailable";
                        if (student == null) {
                            integrityMismatch = true;
                        }

                        items.add(DashboardItem.createStudent(
                                StudentProgress.STATUS_NEEDS_ATTENTION,
                                classId,
                                classOffering.getName(),
                                track != null ? track.getTrackId() : classOffering.getTrackId(),
                                track != null ? track.getCode() : "",
                                track != null ? track.getName() : "",
                                classOffering.getCurrentWeek(),
                                expectedWeekId,
                                sp.getStudentId(),
                                studentName,
                                studentNickname,
                                sp.getBlocker(),
                                sp.getTeacherNote()
                        ));
                    }
                    // NOT_RECORDED, ON_TRACK, COMPLETED, NOT_STARTED do not enter attention feed
                }
            }

            boolean partiallyUnavailable = hasReadFailure || integrityMismatch;

            if (track != null && currentWeek != null) {
                summaries.add(new DashboardClassSummary(
                        classOffering,
                        track,
                        currentWeek,
                        activeStudentIds.size(),
                        blockedCount,
                        needsAttentionCount,
                        partiallyUnavailable
                ));
            }
        }

        // Sort class summaries by Class name
        summaries.sort((a, b) -> a.getClassOffering().getName()
                .compareToIgnoreCase(b.getClassOffering().getName()));

        // 2. Process Active Projects
        if (activeProjects != null && !projectReadFailed) {
            for (Project p : activeProjects) {
                if (p == null || !p.isActive()) continue;

                // Restrict to projects in loaded active classes
                if (!activeClassIds.contains(p.getClassId())) {
                    continue;
                }

                String status = p.getOverallStatus();
                boolean isBlocked = StudentProgress.STATUS_BLOCKED.equals(status);
                boolean isAttention = StudentProgress.STATUS_NEEDS_ATTENTION.equals(status);

                if (!isBlocked && !isAttention) {
                    continue;
                }

                ClassOffering pClass = null;
                for (ClassOffering c : activeClasses) {
                    if (c != null && c.getClassId().equals(p.getClassId())) {
                        pClass = c;
                        break;
                    }
                }

                boolean trackMatches = pClass != null && p.getTrackId() != null
                        && p.getTrackId().equals(pClass.getTrackId());
                boolean weekValid = p.getCurrentWeek() >= 1 && p.getCurrentWeek() <= 10;

                if (!trackMatches || !weekValid) {
                    continue; // Excluded from feed; already flagged in classesWithProjectMismatches
                }

                String className = pClass != null ? pClass.getName() : p.getClassId();
                Track pTrack = (tracksByTrackId != null && pClass != null)
                        ? tracksByTrackId.get(pClass.getTrackId()) : null;
                String trackCode = pTrack != null ? pTrack.getCode() : p.getTrackId();
                String trackName = pTrack != null ? pTrack.getName() : "";
                String weekId = CurriculumCatalog.weekId(p.getCurrentWeek());

                String blocker = null;
                if (isBlocked) {
                    ProjectProgress pp = currentProjectProgressByProjectId != null
                            ? currentProjectProgressByProjectId.get(p.getProjectId()) : null;
                    if (pp != null && p.getProjectId().equals(pp.getProjectId())
                            && p.getClassId().equals(pp.getClassId())
                            && p.getCurrentWeek() == pp.getWeekNumber()) {
                        blocker = pp.getBlocker();
                    }
                }

                items.add(DashboardItem.createProject(
                        status,
                        p.getClassId(),
                        className,
                        p.getTrackId(),
                        trackCode,
                        trackName,
                        p.getCurrentWeek(),
                        weekId,
                        p.getProjectId(),
                        p.getTitle(),
                        p.getTeamName(),
                        blocker
                ));
            }
        }

        // 3. Sort unified intervention items deterministically
        Collections.sort(items);

        return new DerivationResult(summaries, items, projectReadFailed);
    }
}
