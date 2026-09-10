package th.ac.vu.classflow.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Enrollment;

public final class EnrollmentRepository {

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public Task<List<Enrollment>> loadForStudent(String studentId) {
        return firestore.collection("enrollments").whereEqualTo("studentId", studentId).get()
                .continueWith(task -> mapEnrollments(task.getResult()));
    }

    public Task<List<Enrollment>> loadActiveForClass(String classId) {
        return firestore.collection("enrollments")
                .whereEqualTo("classId", classId)
                .whereEqualTo("status", Enrollment.STATUS_ACTIVE).get()
                .continueWith(task -> mapEnrollments(task.getResult()));
    }

    public Task<Enrollment> loadEnrollment(String classId, String studentId) {
        return firestore.collection("enrollments")
                .document(Enrollment.documentId(classId, studentId)).get()
                .continueWith(task -> mapRequiredEnrollment(task.getResult()));
    }

    public Task<Void> createEnrollment(String studentId, String classId) {
        if (blank(studentId) || blank(classId)) {
            return Tasks.forException(new DataValidationException("Student and Class are required."));
        }
        DocumentReference studentRef = firestore.collection("students").document(studentId);
        DocumentReference classRef = firestore.collection("classes").document(classId);
        DocumentReference enrollmentRef = firestore.collection("enrollments")
                .document(Enrollment.documentId(classId, studentId));
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot student = transaction.get(studentRef);
            DocumentSnapshot selectedClass = transaction.get(classRef);
            DocumentSnapshot existing = transaction.get(enrollmentRef);
            if (!student.exists()) throw new DataNotFoundException("The Student was not found.");
            Boolean active = student.getBoolean("active");
            if (!Boolean.TRUE.equals(active)) {
                throw new DataValidationException("Restore this Student before creating a new Enrollment.");
            }
            if (!selectedClass.exists()) throw new DataNotFoundException("The selected Class was not found.");
            String classStatus = selectedClass.getString("status");
            if (!(ClassOffering.STATUS_ACTIVE.equals(classStatus)
                    || ClassOffering.STATUS_PLANNED.equals(classStatus))) {
                throw new DataValidationException("Select an ACTIVE or PLANNED Class.");
            }
            String trackId = selectedClass.getString("trackId");
            if (blank(trackId)) throw new DataValidationException("The selected Class Track is invalid.");
            if (existing.exists()) throw new DuplicateEnrollmentException();

            Map<String, Object> values = new HashMap<>();
            values.put("classId", classId);
            values.put("trackId", trackId);
            values.put("studentId", studentId);
            values.put("status", Enrollment.STATUS_ACTIVE);
            values.put("enrolledAt", FieldValue.serverTimestamp());
            transaction.set(enrollmentRef, values);
            return null;
        });
    }

    public Task<Void> updateStatus(String classId, String studentId, String status) {
        if (!Enrollment.isValidStatus(status)) {
            return Tasks.forException(new DataValidationException("Select a valid Enrollment status."));
        }
        DocumentReference ref = firestore.collection("enrollments")
                .document(Enrollment.documentId(classId, studentId));
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            Enrollment enrollment = mapRequiredEnrollment(snapshot);
            if (!classId.equals(enrollment.getClassId())
                    || !studentId.equals(enrollment.getStudentId())) {
                throw new DataValidationException("The Enrollment identity is inconsistent.");
            }
            transaction.update(ref, "status", status);
            return null;
        });
    }

    private List<Enrollment> mapEnrollments(QuerySnapshot snapshot) {
        List<Enrollment> result = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            result.add(mapRequiredEnrollment(document));
        }
        result.sort(Comparator.comparing(Enrollment::getClassId,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return result;
    }

    private Enrollment mapRequiredEnrollment(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) throw new DataNotFoundException("The Enrollment was not found.");
        Enrollment enrollment = snapshot.toObject(Enrollment.class);
        if (enrollment == null) throw new DataValidationException("The Enrollment data is invalid.");
        enrollment.setEnrollmentId(snapshot.getId());
        if (blank(enrollment.getClassId()) || blank(enrollment.getTrackId())
                || blank(enrollment.getStudentId()) || enrollment.getEnrolledAt() == null
                || !Enrollment.isValidStatus(enrollment.getStatus())
                || !snapshot.getId().equals(Enrollment.documentId(
                enrollment.getClassId(), enrollment.getStudentId()))) {
            throw new DataValidationException("Enrollment " + snapshot.getId()
                    + " has invalid or inconsistent data.");
        }
        return enrollment;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
