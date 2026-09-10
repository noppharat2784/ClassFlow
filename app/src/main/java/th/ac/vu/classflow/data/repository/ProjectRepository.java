package th.ac.vu.classflow.data.repository;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;

public final class ProjectRepository {

    private final FirebaseFirestore firestore;

    public ProjectRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public Task<List<Project>> loadActiveProjects() {
        return firestore.collection("projects")
                .whereEqualTo("active", true)
                .get()
                .continueWith(task -> mapProjects(task.getResult()));
    }

    public Task<List<Project>> loadAllProjects() {
        return firestore.collection("projects")
                .get()
                .continueWith(task -> mapProjects(task.getResult()));
    }

    public Task<List<Project>> loadProjectsForClass(String classId) {
        return firestore.collection("projects")
                .whereEqualTo("active", true)
                .whereEqualTo("classId", classId)
                .get()
                .continueWith(task -> mapProjects(task.getResult()));
    }

    public Task<Project> loadProject(String projectId) {
        if (blank(projectId)) {
            return Tasks.forException(new DataValidationException("Project ID is required."));
        }
        return firestore.collection("projects").document(projectId).get()
                .continueWith(task -> mapRequiredProject(task.getResult()));
    }

    public Task<String> createProject(String classId, String title, @Nullable String teamName, List<String> memberIds) {
        String cleanClassId = trimmed(classId);
        String cleanTitle = trimmed(title);
        String cleanTeamName = trimmed(teamName);
        if (blank(cleanClassId)) {
            return Tasks.forException(new DataValidationException("Class selection is required."));
        }
        if (blank(cleanTitle)) {
            return Tasks.forException(new DataValidationException("Project title is required."));
        }
        if (memberIds == null || memberIds.isEmpty()) {
            return Tasks.forException(new DataValidationException("At least one project member is required."));
        }

        List<String> cleanMemberIds = new ArrayList<>();
        for (String m : memberIds) {
            String cm = trimmed(m);
            if (!cm.isEmpty() && !cleanMemberIds.contains(cm)) {
                cleanMemberIds.add(cm);
            }
        }
        if (cleanMemberIds.isEmpty()) {
            return Tasks.forException(new DataValidationException("At least one project member is required."));
        }

        DocumentReference projectRef = firestore.collection("projects").document();
        String generatedProjectId = projectRef.getId();

        DocumentReference classRef = firestore.collection("classes").document(cleanClassId);

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot classDoc = transaction.get(classRef);
            if (!classDoc.exists()) {
                throw new DataNotFoundException("The selected Class was not found.");
            }

            String trackId = classDoc.getString("trackId");
            if (blank(trackId)) {
                throw new DataValidationException("Class has no configured trackId.");
            }

            DocumentReference trackRef = firestore.collection("tracks").document(trackId);
            DocumentSnapshot trackDoc = transaction.get(trackRef);
            if (!trackDoc.exists()) {
                throw new DataNotFoundException("The curriculum Track was not found.");
            }

            Long classCurrentWeek = classDoc.getLong("currentWeek");
            int initialWeek = classCurrentWeek != null ? classCurrentWeek.intValue() : 1;
            Long totalWeeks = trackDoc.getLong("totalWeeks");
            if (totalWeeks != null && (initialWeek < 1 || initialWeek > totalWeeks.intValue())) {
                initialWeek = 1;
            }

            for (String studentId : cleanMemberIds) {
                DocumentReference studentRef = firestore.collection("students").document(studentId);
                DocumentSnapshot studentDoc = transaction.get(studentRef);
                if (!studentDoc.exists()) {
                    throw new DataNotFoundException("Student " + studentId + " was not found.");
                }
                if (!Boolean.TRUE.equals(studentDoc.getBoolean("active"))) {
                    throw new DataValidationException("Student " + studentId + " is not active.");
                }

                DocumentReference enrollmentRef = firestore.collection("enrollments")
                        .document(Enrollment.documentId(cleanClassId, studentId));
                DocumentSnapshot enrollmentDoc = transaction.get(enrollmentRef);
                if (!enrollmentDoc.exists() || !Enrollment.STATUS_ACTIVE.equals(enrollmentDoc.getString("status"))) {
                    throw new DataValidationException("Student " + studentId + " does not have an ACTIVE enrollment in this class.");
                }
            }

            Map<String, Object> projectData = new LinkedHashMap<>();
            projectData.put("classId", cleanClassId);
            projectData.put("trackId", trackId);
            projectData.put("title", cleanTitle);
            projectData.put("teamName", cleanTeamName.isEmpty() ? null : cleanTeamName);
            projectData.put("memberIds", cleanMemberIds);
            projectData.put("currentWeek", initialWeek);
            projectData.put("overallStatus", Project.STATUS_NOT_STARTED);
            projectData.put("active", true);
            projectData.put("createdAt", FieldValue.serverTimestamp());
            projectData.put("updatedAt", FieldValue.serverTimestamp());

            transaction.set(projectRef, projectData);
            return generatedProjectId;
        });
    }

    public Task<Void> updateProject(String projectId, String title, @Nullable String teamName, List<String> memberIds) {
        String cleanProjectId = trimmed(projectId);
        String cleanTitle = trimmed(title);
        String cleanTeamName = trimmed(teamName);
        if (blank(cleanProjectId)) {
            return Tasks.forException(new DataValidationException("Project ID is required."));
        }
        if (blank(cleanTitle)) {
            return Tasks.forException(new DataValidationException("Project title is required."));
        }
        if (memberIds == null || memberIds.isEmpty()) {
            return Tasks.forException(new DataValidationException("At least one project member is required."));
        }

        List<String> cleanMemberIds = new ArrayList<>();
        for (String m : memberIds) {
            String cm = trimmed(m);
            if (!cm.isEmpty() && !cleanMemberIds.contains(cm)) {
                cleanMemberIds.add(cm);
            }
        }
        if (cleanMemberIds.isEmpty()) {
            return Tasks.forException(new DataValidationException("At least one project member is required."));
        }

        DocumentReference projectRef = firestore.collection("projects").document(cleanProjectId);

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot projectDoc = transaction.get(projectRef);
            if (!projectDoc.exists()) {
                throw new DataNotFoundException("Project not found.");
            }
            if (!Boolean.TRUE.equals(projectDoc.getBoolean("active"))) {
                throw new DataValidationException("Cannot edit an archived project.");
            }

            String classId = projectDoc.getString("classId");
            if (blank(classId)) {
                throw new DataValidationException("Project data is corrupted: missing classId.");
            }

            @SuppressWarnings("unchecked")
            List<String> existingMembers = (List<String>) projectDoc.get("memberIds");
            if (existingMembers == null) {
                existingMembers = Collections.emptyList();
            }

            for (String studentId : cleanMemberIds) {
                DocumentReference studentRef = firestore.collection("students").document(studentId);
                DocumentSnapshot studentDoc = transaction.get(studentRef);
                if (!studentDoc.exists()) {
                    throw new DataNotFoundException("Student " + studentId + " was not found.");
                }

                if (!existingMembers.contains(studentId)) {
                    if (!Boolean.TRUE.equals(studentDoc.getBoolean("active"))) {
                        throw new DataValidationException("New member " + studentId + " is not active.");
                    }
                    DocumentReference enrollmentRef = firestore.collection("enrollments")
                            .document(Enrollment.documentId(classId, studentId));
                    DocumentSnapshot enrollmentDoc = transaction.get(enrollmentRef);
                    if (!enrollmentDoc.exists() || !Enrollment.STATUS_ACTIVE.equals(enrollmentDoc.getString("status"))) {
                        throw new DataValidationException("New member " + studentId + " does not have an ACTIVE enrollment in this class.");
                    }
                }
            }

            transaction.update(projectRef,
                    "title", cleanTitle,
                    "teamName", cleanTeamName.isEmpty() ? null : cleanTeamName,
                    "memberIds", cleanMemberIds,
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public Task<Void> updateCurrentWeek(String projectId, int targetWeekNumber) {
        String cleanProjectId = trimmed(projectId);
        if (blank(cleanProjectId)) {
            return Tasks.forException(new DataValidationException("Project ID is required."));
        }

        DocumentReference projectRef = firestore.collection("projects").document(cleanProjectId);

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot projectDoc = transaction.get(projectRef);
            if (!projectDoc.exists()) {
                throw new DataNotFoundException("Project not found.");
            }
            if (!Boolean.TRUE.equals(projectDoc.getBoolean("active"))) {
                throw new DataValidationException("Cannot change week for an archived project.");
            }

            String classId = projectDoc.getString("classId");
            String trackId = projectDoc.getString("trackId");
            if (blank(classId) || blank(trackId)) {
                throw new DataValidationException("Project data is corrupted: missing classId or trackId.");
            }

            DocumentReference classRef = firestore.collection("classes").document(classId);
            DocumentSnapshot classDoc = transaction.get(classRef);
            if (!classDoc.exists()) {
                throw new DataNotFoundException("Associated Class was not found.");
            }

            DocumentReference trackRef = firestore.collection("tracks").document(trackId);
            DocumentSnapshot trackDoc = transaction.get(trackRef);
            if (!trackDoc.exists()) {
                throw new DataNotFoundException("Associated Track was not found.");
            }

            Long totalWeeks = trackDoc.getLong("totalWeeks");
            int maxWeeks = totalWeeks != null ? totalWeeks.intValue() : 10;
            if (targetWeekNumber < 1 || targetWeekNumber > maxWeeks) {
                throw new DataValidationException("Target week must be between 1 and " + maxWeeks + ".");
            }

            String targetWeekId = String.format(Locale.US, "W%02d", targetWeekNumber);
            DocumentReference weekRef = trackRef.collection("weeks").document(targetWeekId);
            DocumentSnapshot weekDoc = transaction.get(weekRef);
            if (!weekDoc.exists()) {
                throw new DataNotFoundException("Target Curriculum Week " + targetWeekId + " was not found.");
            }

            DocumentReference targetProgressRef = firestore.collection("projectProgress")
                    .document(ProjectProgress.documentId(cleanProjectId, targetWeekId));
            DocumentSnapshot targetProgressDoc = transaction.get(targetProgressRef);

            String newOverallStatus;
            if (targetProgressDoc.exists()) {
                String storedProjectId = targetProgressDoc.getString("projectId");
                String storedClassId = targetProgressDoc.getString("classId");
                String storedTrackId = targetProgressDoc.getString("trackId");
                String storedWeekId = targetProgressDoc.getString("weekId");
                Long storedWeekNumber = targetProgressDoc.getLong("weekNumber");
                String storedStatus = targetProgressDoc.getString("overallStatus");

                if (!cleanProjectId.equals(storedProjectId)
                        || !classId.equals(storedClassId)
                        || !trackId.equals(storedTrackId)
                        || !targetWeekId.equals(storedWeekId)
                        || storedWeekNumber == null || storedWeekNumber.intValue() != targetWeekNumber
                        || !ProjectProgress.isValidOverallStatus(storedStatus)) {
                    throw new DataValidationException("Malformed ProjectProgress data for target week.");
                }
                newOverallStatus = storedStatus;
            } else {
                newOverallStatus = Project.STATUS_NOT_STARTED;
            }

            transaction.update(projectRef,
                    "currentWeek", targetWeekNumber,
                    "overallStatus", newOverallStatus,
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public Task<Void> archiveProject(String projectId) {
        String cleanProjectId = trimmed(projectId);
        if (blank(cleanProjectId)) {
            return Tasks.forException(new DataValidationException("Project ID is required."));
        }
        DocumentReference projectRef = firestore.collection("projects").document(cleanProjectId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot doc = transaction.get(projectRef);
            if (!doc.exists()) {
                throw new DataNotFoundException("Project not found.");
            }
            transaction.update(projectRef,
                    "active", false,
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    private List<Project> mapProjects(QuerySnapshot snapshot) {
        List<Project> result = new ArrayList<>();
        if (snapshot == null) return result;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Project project = mapProject(doc);
            if (project != null) {
                result.add(project);
            }
        }
        result.sort(Comparator.comparing(p -> p.getTitle() != null ? p.getTitle().toLowerCase(Locale.US) : ""));
        return result;
    }

    public Project mapRequiredProject(DocumentSnapshot doc) {
        Project project = mapProject(doc);
        if (project == null) {
            throw new DataNotFoundException("Project " + doc.getId() + " not found or invalid.");
        }
        return project;
    }

    @Nullable
    public Project mapProject(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        Project p = doc.toObject(Project.class);
        if (p == null) return null;
        p.setProjectId(doc.getId());
        if (blank(p.getClassId()) || blank(p.getTrackId()) || blank(p.getTitle())
                || p.getMemberIds() == null || p.getMemberIds().isEmpty()
                || !Project.isValidOverallStatus(p.getOverallStatus())) {
            return null;
        }
        return p;
    }

    private static String trimmed(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
