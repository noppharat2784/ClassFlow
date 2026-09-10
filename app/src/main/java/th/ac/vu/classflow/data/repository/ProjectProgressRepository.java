package th.ac.vu.classflow.data.repository;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;

public final class ProjectProgressRepository {

    private final FirebaseFirestore firestore;

    public ProjectProgressRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public Task<ProjectProgress> loadOne(String projectId, String weekId) {
        String cleanProjectId = trimmed(projectId);
        String cleanWeekId = trimmed(weekId);
        if (blank(cleanProjectId) || blank(cleanWeekId)) {
            return Tasks.forException(new DataValidationException("Project ID and Week ID are required."));
        }
        return firestore.collection("projectProgress")
                .document(ProjectProgress.documentId(cleanProjectId, cleanWeekId))
                .get()
                .continueWith(task -> {
                    DocumentSnapshot doc = task.getResult();
                    return doc.exists() ? mapRequired(doc) : null;
                });
    }

    public Task<List<ProjectProgress>> loadForProject(String projectId) {
        String cleanProjectId = trimmed(projectId);
        if (blank(cleanProjectId)) {
            return Tasks.forException(new DataValidationException("Project ID is required."));
        }
        return firestore.collection("projectProgress")
                .whereEqualTo("projectId", cleanProjectId)
                .get()
                .continueWith(task -> mapMany(task.getResult()));
    }

    public Task<Void> save(ProjectProgress input) {
        if (input == null) {
            throw new DataValidationException("Progress payload cannot be null.");
        }
        String projectId = trimmed(input.getProjectId());
        String classId = trimmed(input.getClassId());
        String trackId = trimmed(input.getTrackId());
        String weekId = trimmed(input.getWeekId());
        String blocker = trimmed(input.getBlocker());
        String teacherNote = trimmed(input.getTeacherNote());

        if (blank(projectId) || blank(classId) || blank(trackId) || blank(weekId)) {
            throw new DataValidationException("Project progress identity is incomplete.");
        }
        if (input.getWeekNumber() < 1 || input.getWeekNumber() > 10) {
            throw new DataValidationException("Week number must be between 1 and 10.");
        }
        if (teacherNote.length() > 500) {
            throw new DataValidationException("Teacher Note must be 500 characters or fewer.");
        }
        if (!ProjectProgress.isValidOverallStatus(input.getOverallStatus())) {
            throw new DataValidationException("Select a valid overall status.");
        }
        if (ProjectProgress.STATUS_BLOCKED.equals(input.getOverallStatus()) && blocker.isEmpty()) {
            throw new DataValidationException("Blocker is required when overall status is BLOCKED.");
        }

        DocumentReference projectRef = firestore.collection("projects").document(projectId);
        DocumentReference classRef = firestore.collection("classes").document(classId);
        DocumentReference trackRef = firestore.collection("tracks").document(trackId);
        DocumentReference weekRef = trackRef.collection("weeks").document(weekId);
        DocumentReference progressRef = firestore.collection("projectProgress")
                .document(ProjectProgress.documentId(projectId, weekId));

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot projectDoc = transaction.get(projectRef);
            DocumentSnapshot classDoc = transaction.get(classRef);
            DocumentSnapshot trackDoc = transaction.get(trackRef);
            DocumentSnapshot weekDoc = transaction.get(weekRef);
            DocumentSnapshot existing = transaction.get(progressRef);

            if (!projectDoc.exists()) {
                throw new DataNotFoundException("The Project was not found.");
            }
            if (!Boolean.TRUE.equals(projectDoc.getBoolean("active"))) {
                throw new DataValidationException("Cannot edit progress for an archived Project.");
            }
            if (!classDoc.exists()) {
                throw new DataNotFoundException("The Class was not found.");
            }
            if (!trackDoc.exists()) {
                throw new DataNotFoundException("The curriculum Track was not found.");
            }
            if (!weekDoc.exists()) {
                throw new DataNotFoundException("The curriculum Week was not found.");
            }

            String projectTrackId = projectDoc.getString("trackId");
            String classTrackId = classDoc.getString("trackId");
            if (!trackId.equals(projectTrackId)
                    || !trackId.equals(classTrackId)
                    || !trackId.equals(trackDoc.getId())) {
                throw new DataValidationException("Project, Class, and Track identities do not agree.");
            }
            if (!classId.equals(projectDoc.getString("classId"))) {
                throw new DataValidationException("Project and Class identities do not agree.");
            }

            Long storedWeekNumber = weekDoc.getLong("weekNumber");
            String expectedWeekId = String.format(Locale.US, "W%02d", input.getWeekNumber());
            if (storedWeekNumber == null || storedWeekNumber.intValue() != input.getWeekNumber()
                    || !weekId.equals(expectedWeekId)) {
                throw new DataValidationException("Week ID and Week number do not agree.");
            }

            List<String> expectedKeys = ProgressMetricSchema.resolve(trackId,
                    weekDoc.getString("progressType"), input.getWeekNumber());
            validateMetrics(input.getMetrics(), expectedKeys);

            if (existing.exists()) {
                ProjectProgress stored = mapRequired(existing);
                if (!projectId.equals(stored.getProjectId())
                        || !classId.equals(stored.getClassId())
                        || !trackId.equals(stored.getTrackId())
                        || !weekId.equals(stored.getWeekId())) {
                    throw new DataValidationException("Existing Progress identity is inconsistent.");
                }
            }

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("projectId", projectId);
            values.put("classId", classId);
            values.put("trackId", trackId);
            values.put("weekId", weekId);
            values.put("weekNumber", input.getWeekNumber());
            values.put("metrics", new LinkedHashMap<>(input.getMetrics()));
            values.put("overallStatus", input.getOverallStatus());
            values.put("blocker", blocker);
            values.put("teacherNote", teacherNote);
            values.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(progressRef, values);

            Long projectCurrentWeek = projectDoc.getLong("currentWeek");
            if (projectCurrentWeek != null && projectCurrentWeek.intValue() == input.getWeekNumber()) {
                transaction.update(projectRef,
                        "overallStatus", input.getOverallStatus(),
                        "updatedAt", FieldValue.serverTimestamp());
            }

            return null;
        });
    }

    private List<ProjectProgress> mapMany(QuerySnapshot snapshot) {
        List<ProjectProgress> result = new ArrayList<>();
        if (snapshot == null) return result;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            result.add(mapRequired(doc));
        }
        result.sort(Comparator.comparingInt(ProjectProgress::getWeekNumber));
        return result;
    }

    public ProjectProgress mapRequired(DocumentSnapshot doc) {
        ProjectProgress progress = doc.toObject(ProjectProgress.class);
        if (progress == null) {
            throw new DataValidationException("The Project Progress data is invalid.");
        }
        progress.setProgressId(doc.getId());
        if (blank(progress.getProjectId())
                || blank(progress.getClassId())
                || blank(progress.getTrackId())
                || blank(progress.getWeekId())
                || progress.getWeekNumber() < 1
                || progress.getUpdatedAt() == null
                || progress.getBlocker() == null
                || progress.getTeacherNote() == null
                || progress.getTeacherNote().length() > 500
                || !ProjectProgress.isValidOverallStatus(progress.getOverallStatus())
                || !doc.getId().equals(ProjectProgress.documentId(progress.getProjectId(), progress.getWeekId()))) {
            throw new DataValidationException("Project Progress " + doc.getId() + " has invalid or inconsistent data.");
        }
        return progress;
    }

    private void validateMetrics(Map<String, String> metrics, List<String> expectedKeys) {
        if (metrics == null) {
            throw new DataValidationException("Metrics payload is missing.");
        }
        Set<String> expectedSet = new LinkedHashSet<>(expectedKeys);
        if (!metrics.keySet().equals(expectedSet)) {
            throw new DataValidationException("Metrics keys do not match the curriculum schema for this week.");
        }
        for (Map.Entry<String, String> entry : metrics.entrySet()) {
            if (!ProjectProgress.isValidMetricValue(entry.getValue())) {
                throw new DataValidationException("Metric " + entry.getKey() + " has an invalid value.");
            }
        }
    }

    private static String trimmed(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
