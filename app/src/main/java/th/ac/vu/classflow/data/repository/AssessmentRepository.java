package th.ac.vu.classflow.data.repository;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import th.ac.vu.classflow.data.assessment.AssessmentRubricCatalog;
import th.ac.vu.classflow.data.model.AssessmentData;
import th.ac.vu.classflow.data.model.Enrollment;

public final class AssessmentRepository {

    private final FirebaseFirestore firestore;

    public AssessmentRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public AssessmentRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public Task<AssessmentData> loadAssessment(String classId, String studentId) {
        String enrollmentId = Enrollment.documentId(classId, studentId);
        return firestore.collection("enrollments").document(enrollmentId).get()
                .continueWith(task -> {
                    DocumentSnapshot snapshot = task.getResult();
                    if (!snapshot.exists()) {
                        throw new DataNotFoundException("The Enrollment was not found.");
                    }
                    Object assessmentRaw = snapshot.get("assessment");
                    if (assessmentRaw instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) assessmentRaw;
                        return AssessmentData.fromMap(map);
                    }
                    return null;
                });
    }

    public Task<Void> saveAssessment(String classId, String studentId, AssessmentData input) {
        String trimmedClassId = classId == null ? "" : classId.trim();
        String trimmedStudentId = studentId == null ? "" : studentId.trim();

        if (trimmedClassId.isEmpty() || trimmedStudentId.isEmpty()) {
            return Tasks.forException(new DataValidationException("Class and Student IDs are required."));
        }
        if (input == null) {
            return Tasks.forException(new DataValidationException("Assessment data is required."));
        }

        String strength = input.getStrength().trim();
        String nextStep = input.getNextStep().trim();
        if (strength.length() > 500) {
            return Tasks.forException(new DataValidationException("Strength must be 500 characters or fewer."));
        }
        if (nextStep.length() > 500) {
            return Tasks.forException(new DataValidationException("Next Step must be 500 characters or fewer."));
        }

        Map<String, Object> criteria = input.getCriteria();
        if (criteria == null) {
            return Tasks.forException(new DataValidationException("Assessment criteria map is required."));
        }

        List<String> expectedKeys = AssessmentRubricCatalog.getApprovedCriteriaKeys();
        Set<String> actualKeys = new LinkedHashSet<>(criteria.keySet());
        if (!actualKeys.equals(new LinkedHashSet<>(expectedKeys))) {
            return Tasks.forException(new DataValidationException("Assessment criteria must contain exactly the 15 approved keys."));
        }

        for (String key : expectedKeys) {
            Object val = criteria.get(key);
            if (!AssessmentRubricCatalog.isValidCriterionValue(val)) {
                return Tasks.forException(new DataValidationException("Invalid score for criterion " + key + "."));
            }
        }

        DocumentReference studentRef = firestore.collection("students").document(trimmedStudentId);
        DocumentReference classRef = firestore.collection("classes").document(trimmedClassId);
        DocumentReference enrollmentRef = firestore.collection("enrollments")
                .document(Enrollment.documentId(trimmedClassId, trimmedStudentId));

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot studentSnap = transaction.get(studentRef);
            DocumentSnapshot classSnap = transaction.get(classRef);
            DocumentSnapshot enrollmentSnap = transaction.get(enrollmentRef);

            if (!studentSnap.exists()) {
                throw new DataNotFoundException("The Student was not found.");
            }
            if (!classSnap.exists()) {
                throw new DataNotFoundException("The Class was not found.");
            }
            if (!enrollmentSnap.exists()) {
                throw new DataNotFoundException("The Enrollment was not found.");
            }

            String storedClassId = enrollmentSnap.getString("classId");
            String storedStudentId = enrollmentSnap.getString("studentId");
            String storedEnrollmentTrackId = enrollmentSnap.getString("trackId");
            String storedClassTrackId = classSnap.getString("trackId");
            String storedStatus = enrollmentSnap.getString("status");

            if (!trimmedClassId.equals(storedClassId) || !trimmedStudentId.equals(storedStudentId)) {
                throw new DataValidationException("The Enrollment identity does not match.");
            }
            if (storedClassTrackId == null || !storedClassTrackId.equals(storedEnrollmentTrackId)) {
                throw new DataValidationException("Enrollment Track does not match its Class Track.");
            }
            if (!AssessmentRubricCatalog.TRACK_ID_SS1.equalsIgnoreCase(storedEnrollmentTrackId)) {
                throw new DataValidationException("Assessment rubric not configured for this Track.");
            }
            if (!Enrollment.STATUS_ACTIVE.equals(storedStatus)) {
                throw new DataValidationException("Assessment is read-only because this Enrollment is not ACTIVE.");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("rubricId", AssessmentRubricCatalog.RUBRIC_ID_SS1);
            payload.put("criteria", new HashMap<>(criteria));
            payload.put("strength", strength);
            payload.put("nextStep", nextStep);
            payload.put("updatedAt", FieldValue.serverTimestamp());

            transaction.update(enrollmentRef, "assessment", payload);
            return null;
        });
    }
}
