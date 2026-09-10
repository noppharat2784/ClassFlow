package th.ac.vu.classflow.data.repository;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
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
import java.util.Map;
import java.util.Set;

import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.StudentProgress;

public final class StudentProgressRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public Task<StudentProgress> loadOne(String classId, String studentId, String weekId) {
        return firestore.collection("studentProgress")
                .document(StudentProgress.documentId(classId, studentId, weekId)).get()
                .continueWith(task -> task.getResult().exists()
                        ? mapRequired(task.getResult()) : null);
    }

    public Task<List<StudentProgress>> loadForStudentClass(String studentId, String classId) {
        return firestore.collection("studentProgress")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("classId", classId).get()
                .continueWith(task -> mapMany(task.getResult()));
    }

    public Task<List<StudentProgress>> loadForClassWeek(String classId, String weekId) {
        return firestore.collection("studentProgress")
                .whereEqualTo("classId", classId)
                .whereEqualTo("weekId", weekId).get()
                .continueWith(task -> mapMany(task.getResult()));
    }

    public Task<List<StudentProgress>> loadForClassWeekNumber(String classId, int weekNumber) {
        return firestore.collection("studentProgress")
                .whereEqualTo("classId", classId)
                .whereEqualTo("weekNumber", weekNumber).get()
                .continueWith(task -> mapMany(task.getResult()));
    }

    public Task<Void> save(StudentProgress input) {
        String classId = trimmed(input.getClassId());
        String trackId = trimmed(input.getTrackId());
        String studentId = trimmed(input.getStudentId());
        String weekId = trimmed(input.getWeekId());
        String blocker = trimmed(input.getBlocker());
        String teacherNote = trimmed(input.getTeacherNote());
        if (blank(classId) || blank(trackId) || blank(studentId) || blank(weekId)) {
            throw new DataValidationException("Progress identity is incomplete.");
        }
        if (teacherNote.length() > 500) {
            throw new DataValidationException("Teacher Note must be 500 characters or fewer.");
        }
        if (!StudentProgress.isValidOverallStatus(input.getOverallStatus())) {
            throw new DataValidationException("Select a valid overall status.");
        }
        if (StudentProgress.STATUS_BLOCKED.equals(input.getOverallStatus()) && blocker.isEmpty()) {
            throw new DataValidationException("Blocker is required when overall status is BLOCKED.");
        }

        DocumentReference studentRef = firestore.collection("students").document(studentId);
        DocumentReference classRef = firestore.collection("classes").document(classId);
        DocumentReference enrollmentRef = firestore.collection("enrollments")
                .document(Enrollment.documentId(classId, studentId));
        DocumentReference trackRef = firestore.collection("tracks").document(trackId);
        DocumentReference weekRef = trackRef.collection("weeks").document(weekId);
        DocumentReference progressRef = firestore.collection("studentProgress")
                .document(StudentProgress.documentId(classId, studentId, weekId));

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot student = transaction.get(studentRef);
            DocumentSnapshot selectedClass = transaction.get(classRef);
            DocumentSnapshot enrollment = transaction.get(enrollmentRef);
            DocumentSnapshot track = transaction.get(trackRef);
            DocumentSnapshot week = transaction.get(weekRef);
            DocumentSnapshot existing = transaction.get(progressRef);

            if (!student.exists()) throw new DataNotFoundException("The Student was not found.");
            if (!selectedClass.exists()) throw new DataNotFoundException("The Class was not found.");
            if (!enrollment.exists()) throw new DataNotFoundException("The Enrollment was not found.");
            if (!track.exists()) throw new DataNotFoundException("The curriculum Track was not found.");
            if (!week.exists()) throw new DataNotFoundException("The curriculum Week was not found.");

            String classTrackId = selectedClass.getString("trackId");
            if (!trackId.equals(classTrackId) || !trackId.equals(track.getId())
                    || !classId.equals(enrollment.getString("classId"))
                    || !studentId.equals(enrollment.getString("studentId"))
                    || !trackId.equals(enrollment.getString("trackId"))) {
                throw new DataValidationException("Student, Class, Enrollment, and Track identities do not agree.");
            }
            if (!Enrollment.STATUS_ACTIVE.equals(enrollment.getString("status"))) {
                throw new DataValidationException("Progress is read-only because this Enrollment is not ACTIVE.");
            }
            Long storedWeekNumber = week.getLong("weekNumber");
            String expectedWeekId = String.format(java.util.Locale.US, "W%02d", input.getWeekNumber());
            if (storedWeekNumber == null || storedWeekNumber.intValue() != input.getWeekNumber()
                    || !weekId.equals(expectedWeekId)) {
                throw new DataValidationException("Week ID and Week number do not agree.");
            }
            List<String> expectedKeys = ProgressMetricSchema.resolve(trackId,
                    week.getString("progressType"), input.getWeekNumber());
            validateMetrics(input.getMetrics(), expectedKeys);
            if (existing.exists()) {
                StudentProgress stored = mapRequired(existing);
                if (!classId.equals(stored.getClassId()) || !studentId.equals(stored.getStudentId())
                        || !weekId.equals(stored.getWeekId()) || !trackId.equals(stored.getTrackId())) {
                    throw new DataValidationException("Existing Progress identity is inconsistent.");
                }
            }

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("classId", classId);
            values.put("trackId", trackId);
            values.put("studentId", studentId);
            values.put("weekId", weekId);
            values.put("weekNumber", input.getWeekNumber());
            values.put("metrics", new LinkedHashMap<>(input.getMetrics()));
            values.put("overallStatus", input.getOverallStatus());
            values.put("blocker", blocker);
            values.put("teacherNote", teacherNote);
            values.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(progressRef, values);
            return null;
        });
    }

    private List<StudentProgress> mapMany(QuerySnapshot snapshot) {
        List<StudentProgress> result = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) result.add(mapRequired(document));
        result.sort(Comparator.comparingInt(StudentProgress::getWeekNumber));
        return result;
    }

    private StudentProgress mapRequired(DocumentSnapshot snapshot) {
        StudentProgress progress = snapshot.toObject(StudentProgress.class);
        if (progress == null) throw new DataValidationException("The Student Progress data is invalid.");
        progress.setProgressId(snapshot.getId());
        if (blank(progress.getClassId()) || blank(progress.getTrackId())
                || blank(progress.getStudentId()) || blank(progress.getWeekId())
                || progress.getWeekNumber() < 1 || progress.getUpdatedAt() == null
                || progress.getBlocker() == null || progress.getTeacherNote() == null
                || progress.getTeacherNote().length() > 500
                || !StudentProgress.isValidOverallStatus(progress.getOverallStatus())
                || !snapshot.getId().equals(StudentProgress.documentId(progress.getClassId(),
                progress.getStudentId(), progress.getWeekId()))) {
            throw new DataValidationException("Student Progress " + snapshot.getId()
                    + " has invalid or inconsistent data.");
        }
        if (StudentProgress.STATUS_BLOCKED.equals(progress.getOverallStatus())
                && progress.getBlocker().trim().isEmpty()) {
            throw new DataValidationException("Stored BLOCKED Progress has no blocker.");
        }
        for (String value : progress.getMetrics().values()) {
            if (!StudentProgress.isValidMetricValue(value)) {
                throw new DataValidationException("Student Progress has an invalid metric value.");
            }
        }
        return progress;
    }

    private void validateMetrics(Map<String, String> metrics, List<String> expected) {
        if (metrics == null) throw new DataValidationException("All Progress metrics are required.");
        Set<String> actualKeys = new LinkedHashSet<>(metrics.keySet());
        if (!actualKeys.equals(new LinkedHashSet<>(expected))) {
            throw new DataValidationException("Progress metrics do not match this Track and Week.");
        }
        for (String key : expected) {
            if (!StudentProgress.isValidMetricValue(metrics.get(key))) {
                throw new DataValidationException("Select a valid value for "
                        + ProgressMetricSchema.label(key) + ".");
            }
        }
    }

    private static String trimmed(@Nullable String value) { return value == null ? "" : value.trim(); }
    private static boolean blank(@Nullable String value) { return value == null || value.trim().isEmpty(); }
}
