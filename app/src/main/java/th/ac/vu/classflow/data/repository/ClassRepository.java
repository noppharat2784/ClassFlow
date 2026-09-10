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

import th.ac.vu.classflow.data.bootstrap.CurriculumCatalog;
import th.ac.vu.classflow.data.model.ClassOffering;

public final class ClassRepository {

    private final FirebaseFirestore firestore;

    public ClassRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public Task<List<ClassOffering>> loadActiveClasses() {
        return firestore.collection("classes")
                .whereEqualTo("status", ClassOffering.STATUS_ACTIVE)
                .get()
                .continueWith(task -> mapClasses(task.getResult()));
    }

    public Task<List<ClassOffering>> loadAllClasses() {
        return firestore.collection("classes")
                .get()
                .continueWith(task -> mapClasses(task.getResult()));
    }

    public Task<ClassOffering> loadClass(String classId) {
        return firestore.collection("classes").document(classId).get()
                .continueWith(task -> mapRequiredClass(task.getResult()));
    }

    public Task<Void> createClass(ClassOffering classOffering) {
        String validation = validateBasicFields(classOffering);
        if (validation != null) {
            return Tasks.forException(new DataValidationException(validation));
        }

        DocumentReference classReference = firestore.collection("classes")
                .document(classOffering.getClassId());
        DocumentReference trackReference = firestore.collection("tracks")
                .document(classOffering.getTrackId());
        DocumentReference weekReference = trackReference.collection("weeks")
                .document(CurriculumCatalog.weekId(classOffering.getCurrentWeek()));

        return firestore.runTransaction(transaction -> {
            DocumentSnapshot existingClass = transaction.get(classReference);
            DocumentSnapshot track = transaction.get(trackReference);
            DocumentSnapshot week = transaction.get(weekReference);

            if (existingClass.exists()) {
                throw new DuplicateClassException(classOffering.getClassId());
            }
            validateTrackAndWeek(track, week, classOffering.getCurrentWeek(), true);

            Map<String, Object> values = new HashMap<>();
            values.put("classId", classOffering.getClassId());
            values.put("name", classOffering.getName());
            values.put("trackId", classOffering.getTrackId());
            values.put("currentWeek", classOffering.getCurrentWeek());
            values.put("status", classOffering.getStatus());
            values.put("startDate", classOffering.getStartDate());
            values.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(classReference, values);
            return null;
        });
    }

    public Task<Void> updateClassMetadata(String classId, String name, int currentWeek,
                                          String status,
                                          com.google.firebase.Timestamp startDate) {
        ClassOffering input = new ClassOffering(classId, name, "pending", currentWeek,
                status, startDate);
        String validation = validateBasicFields(input);
        if (validation != null) {
            return Tasks.forException(new DataValidationException(validation));
        }

        DocumentReference classReference = firestore.collection("classes").document(classId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot existingClass = transaction.get(classReference);
            if (!existingClass.exists()) {
                throw new DataNotFoundException("The Class was not found.");
            }
            String immutableTrackId = existingClass.getString("trackId");
            if (immutableTrackId == null || immutableTrackId.trim().isEmpty()) {
                throw new DataValidationException("The Class Track reference is invalid.");
            }

            DocumentReference trackReference = firestore.collection("tracks")
                    .document(immutableTrackId);
            DocumentReference weekReference = trackReference.collection("weeks")
                    .document(CurriculumCatalog.weekId(currentWeek));
            DocumentSnapshot track = transaction.get(trackReference);
            DocumentSnapshot week = transaction.get(weekReference);
            validateTrackAndWeek(track, week, currentWeek, false);

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", name);
            updates.put("currentWeek", currentWeek);
            updates.put("status", status);
            updates.put("startDate", startDate);
            transaction.update(classReference, updates);
            return null;
        });
    }

    public Task<Void> updateLifecycleStatus(String classId, String status) {
        if (!ClassOffering.isValidStatus(status)) {
            return Tasks.forException(new DataValidationException("Select a valid Class status."));
        }
        return firestore.collection("classes").document(classId).update("status", status);
    }

    public Task<Void> updateCurrentWeek(String classId, int currentWeek) {
        if (currentWeek < 1) {
            return Tasks.forException(new DataValidationException("Select a valid curriculum Week."));
        }
        DocumentReference classReference = firestore.collection("classes").document(classId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot classSnapshot = transaction.get(classReference);
            if (!classSnapshot.exists()) {
                throw new DataNotFoundException("The Class was not found.");
            }
            String trackId = classSnapshot.getString("trackId");
            if (trackId == null || trackId.trim().isEmpty()) {
                throw new DataValidationException("The Class Track reference is invalid.");
            }
            DocumentReference trackReference = firestore.collection("tracks").document(trackId);
            DocumentReference weekReference = trackReference.collection("weeks")
                    .document(CurriculumCatalog.weekId(currentWeek));
            DocumentSnapshot track = transaction.get(trackReference);
            DocumentSnapshot week = transaction.get(weekReference);
            validateTrackAndWeek(track, week, currentWeek, false);

            // Deliberately update this Class document and this field only. No cascade.
            transaction.update(classReference, "currentWeek", currentWeek);
            return null;
        });
    }

    private List<ClassOffering> mapClasses(QuerySnapshot snapshot) {
        List<ClassOffering> classes = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            ClassOffering classOffering = document.toObject(ClassOffering.class);
            if (classOffering == null) {
                throw new DataValidationException("Class " + document.getId()
                        + " has invalid data.");
            }
            validateLoadedClass(document, classOffering);
            classes.add(classOffering);
        }
        classes.sort(Comparator.comparing(ClassOffering::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return classes;
    }

    private ClassOffering mapRequiredClass(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            throw new DataNotFoundException("The Class was not found.");
        }
        ClassOffering classOffering = snapshot.toObject(ClassOffering.class);
        if (classOffering == null) {
            throw new DataValidationException("The Class data is invalid.");
        }
        validateLoadedClass(snapshot, classOffering);
        return classOffering;
    }

    private void validateLoadedClass(DocumentSnapshot snapshot, ClassOffering classOffering) {
        if (!snapshot.getId().equals(classOffering.getClassId())
                || classOffering.getName() == null || classOffering.getName().trim().isEmpty()
                || classOffering.getTrackId() == null
                || classOffering.getTrackId().trim().isEmpty()
                || classOffering.getCurrentWeek() < 1
                || !ClassOffering.isValidStatus(classOffering.getStatus())
                || classOffering.getStartDate() == null) {
            throw new DataValidationException("Class " + snapshot.getId()
                    + " has invalid or inconsistent data.");
        }
    }

    private String validateBasicFields(ClassOffering classOffering) {
        if (classOffering.getClassId() == null || classOffering.getClassId().trim().isEmpty()) {
            return "Class ID is required.";
        }
        if (classOffering.getClassId().contains("/")) {
            return "Class ID cannot contain a slash.";
        }
        if (classOffering.getName() == null || classOffering.getName().trim().isEmpty()) {
            return "Class name is required.";
        }
        if (classOffering.getTrackId() == null || classOffering.getTrackId().trim().isEmpty()) {
            return "Track is required.";
        }
        if (classOffering.getCurrentWeek() < 1) {
            return "Current Week is required.";
        }
        if (!ClassOffering.isValidStatus(classOffering.getStatus())) {
            return "Select a valid Class status.";
        }
        if (classOffering.getStartDate() == null) {
            return "Start Date is required.";
        }
        return null;
    }

    private void validateTrackAndWeek(DocumentSnapshot track, DocumentSnapshot week,
                                      int currentWeek, boolean requireActiveTrack) {
        if (!track.exists()) {
            throw new DataNotFoundException("The selected Track was not found.");
        }
        if (requireActiveTrack && !Boolean.TRUE.equals(track.getBoolean("active"))) {
            throw new DataValidationException("Select an active Track.");
        }
        Long totalWeeks = track.getLong("totalWeeks");
        if (totalWeeks == null || currentWeek > totalWeeks) {
            throw new DataValidationException("Current Week is outside the selected Track.");
        }
        if (!week.exists()) {
            throw new DataNotFoundException("The selected curriculum Week was not found.");
        }
        Long storedWeekNumber = week.getLong("weekNumber");
        if (storedWeekNumber == null || storedWeekNumber.intValue() != currentWeek) {
            throw new DataValidationException("The selected Week data is inconsistent.");
        }
    }
}
