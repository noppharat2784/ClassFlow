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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Student;

public final class StudentRepository {

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public Task<List<Student>> loadStudents(boolean active) {
        return firestore.collection("students").whereEqualTo("active", active).get()
                .continueWith(task -> mapStudents(task.getResult()));
    }

    public Task<Student> loadStudent(String studentId) {
        return firestore.collection("students").document(studentId).get()
                .continueWith(task -> mapRequiredStudent(task.getResult()));
    }

    public Task<Void> createStudent(Student input, @Nullable String initialClassId) {
        String error = validateIdentity(input.getStudentId(), input.getName(), input.getNickname());
        if (error != null) {
            return Tasks.forException(new DataValidationException(error));
        }
        String studentId = input.getStudentId().trim();
        String name = input.getName().trim();
        String nickname = input.getNickname() == null ? "" : input.getNickname().trim();
        String classId = initialClassId == null ? "" : initialClassId.trim();
        DocumentReference studentRef = firestore.collection("students").document(studentId);

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot studentSnapshot = transaction.get(studentRef);
            if (studentSnapshot.exists()) {
                throw new DuplicateStudentException(studentId);
            }

            DocumentReference classRef = null;
            DocumentReference enrollmentRef = null;
            DocumentSnapshot classSnapshot = null;
            DocumentSnapshot enrollmentSnapshot = null;
            if (!classId.isEmpty()) {
                classRef = firestore.collection("classes").document(classId);
                enrollmentRef = firestore.collection("enrollments")
                        .document(Enrollment.documentId(classId, studentId));
                classSnapshot = transaction.get(classRef);
                enrollmentSnapshot = transaction.get(enrollmentRef);
                validateEligibleClass(classSnapshot);
                if (enrollmentSnapshot.exists()) {
                    throw new DuplicateEnrollmentException();
                }
            }

            Map<String, Object> student = new HashMap<>();
            student.put("studentId", studentId);
            student.put("name", name);
            student.put("nickname", nickname);
            student.put("active", true);
            student.put("createdAt", FieldValue.serverTimestamp());
            student.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(studentRef, student);

            if (classSnapshot != null && enrollmentRef != null) {
                Map<String, Object> enrollment = new HashMap<>();
                enrollment.put("studentId", studentId);
                enrollment.put("classId", classId);
                enrollment.put("trackId", classSnapshot.getString("trackId"));
                enrollment.put("status", Enrollment.STATUS_ACTIVE);
                enrollment.put("enrolledAt", FieldValue.serverTimestamp());
                transaction.set(enrollmentRef, enrollment);
            }
            return null;
        });
    }

    public Task<Void> updateProfile(String studentId, String name, String nickname) {
        String error = validateIdentity(studentId, name, nickname);
        if (error != null) {
            return Tasks.forException(new DataValidationException(error));
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name.trim());
        updates.put("nickname", nickname == null ? "" : nickname.trim());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        return firestore.collection("students").document(studentId).update(updates);
    }

    public Task<Void> archiveStudent(String studentId) { return setActive(studentId, false); }
    public Task<Void> restoreStudent(String studentId) { return setActive(studentId, true); }

    private Task<Void> setActive(String studentId, boolean active) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("active", active);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        return firestore.collection("students").document(studentId).update(updates);
    }

    private List<Student> mapStudents(QuerySnapshot snapshot) {
        List<Student> result = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            result.add(mapRequiredStudent(document));
        }
        result.sort(Comparator.comparing(Student::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return result;
    }

    private Student mapRequiredStudent(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            throw new DataNotFoundException("The Student was not found.");
        }
        Student student = snapshot.toObject(Student.class);
        if (student == null || !snapshot.getId().equals(student.getStudentId())
                || validateIdentity(student.getStudentId(), student.getName(),
                student.getNickname()) != null || student.getCreatedAt() == null
                || student.getUpdatedAt() == null) {
            throw new DataValidationException("Student " + snapshot.getId()
                    + " has invalid or inconsistent data.");
        }
        return student;
    }

    private String validateIdentity(String studentId, String name, String nickname) {
        String id = studentId == null ? "" : studentId.trim();
        String fullName = name == null ? "" : name.trim();
        String shortName = nickname == null ? "" : nickname.trim();
        if (id.isEmpty()) return "Student ID is required.";
        if (id.contains("/")) return "Student ID cannot contain a slash.";
        if (fullName.isEmpty() || fullName.length() > 80) return "Name must be 1–80 characters.";
        if (shortName.length() > 40) return "Nickname must be 40 characters or fewer.";
        return null;
    }

    private void validateEligibleClass(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) throw new DataNotFoundException("The selected Class was not found.");
        String status = snapshot.getString("status");
        String trackId = snapshot.getString("trackId");
        if (!(ClassOffering.STATUS_ACTIVE.equals(status)
                || ClassOffering.STATUS_PLANNED.equals(status))) {
            throw new DataValidationException("Select an ACTIVE or PLANNED Class.");
        }
        if (trackId == null || trackId.trim().isEmpty()) {
            throw new DataValidationException("The selected Class Track reference is invalid.");
        }
    }
}
