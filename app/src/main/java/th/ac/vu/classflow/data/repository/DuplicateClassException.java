package th.ac.vu.classflow.data.repository;

public final class DuplicateClassException extends RuntimeException {
    public DuplicateClassException(String classId) {
        super("A class with ID " + classId + " already exists.");
    }
}
