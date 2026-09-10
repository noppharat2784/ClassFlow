package th.ac.vu.classflow.data.firebase;

import com.google.firebase.firestore.FirebaseFirestoreException;

import th.ac.vu.classflow.data.repository.DataNotFoundException;
import th.ac.vu.classflow.data.repository.DataValidationException;
import th.ac.vu.classflow.data.repository.DuplicateClassException;
import th.ac.vu.classflow.data.repository.DuplicateEnrollmentException;
import th.ac.vu.classflow.data.repository.DuplicateStudentException;

public final class FirestoreErrorMessages {

    private FirestoreErrorMessages() {
    }

    public static String forException(Exception exception) {
        if (exception == null) {
            return "ClassFlow could not complete this request. Please try again.";
        }
        Throwable cause = unwrap(exception);
        if (cause instanceof DuplicateClassException
                || cause instanceof DuplicateStudentException
                || cause instanceof DuplicateEnrollmentException
                || cause instanceof DataNotFoundException
                || cause instanceof DataValidationException) {
            return cause.getMessage();
        }
        if (cause instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) cause;
            switch (firestoreException.getCode()) {
                case PERMISSION_DENIED:
                case UNAUTHENTICATED:
                    return "Permission denied. Confirm that you are signed in and Firestore rules are configured.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "ClassFlow cannot reach Firestore. Check your connection and try again.";
                case NOT_FOUND:
                    return "The requested Firestore data was not found.";
                default:
                    return "Firestore could not complete this request. Please try again.";
            }
        }
        return "ClassFlow could not complete this request. Please try again.";
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.ExecutionException
                || current instanceof RuntimeException && current.getClass().getSimpleName().contains("Execution"))) {
            current = current.getCause();
        }
        return current;
    }
}
