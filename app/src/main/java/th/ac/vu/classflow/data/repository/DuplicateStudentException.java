package th.ac.vu.classflow.data.repository;

public final class DuplicateStudentException extends RuntimeException {
    public DuplicateStudentException(String studentId) {
        super("Student ID " + studentId + " already exists.");
    }
}
