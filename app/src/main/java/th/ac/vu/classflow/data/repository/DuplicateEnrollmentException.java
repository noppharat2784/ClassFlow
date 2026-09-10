package th.ac.vu.classflow.data.repository;

public final class DuplicateEnrollmentException extends RuntimeException {
    public DuplicateEnrollmentException() {
        super("Student is already enrolled in this class. Manage the existing enrollment instead.");
    }
}
