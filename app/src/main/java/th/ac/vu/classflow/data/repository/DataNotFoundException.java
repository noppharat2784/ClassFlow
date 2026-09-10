package th.ac.vu.classflow.data.repository;

public final class DataNotFoundException extends RuntimeException {
    public DataNotFoundException(String message) {
        super(message);
    }
}
