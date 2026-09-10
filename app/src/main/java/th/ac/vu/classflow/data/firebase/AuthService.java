package th.ac.vu.classflow.data.firebase;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import th.ac.vu.classflow.BuildConfig;

public final class AuthService {

    private static final AuthService INSTANCE = new AuthService();

    @Nullable
    private final FirebaseAuth firebaseAuth;

    private AuthService() {
        FirebaseAuth resolvedAuth = null;
        if (BuildConfig.FIREBASE_CONFIGURED) {
            try {
                resolvedAuth = FirebaseAuth.getInstance();
            } catch (IllegalStateException ignored) {
                // The UI treats an unavailable default Firebase app as unconfigured.
            }
        }
        firebaseAuth = resolvedAuth;
    }

    public static AuthService getInstance() {
        return INSTANCE;
    }

    public boolean isConfigured() {
        return firebaseAuth != null;
    }

    @Nullable
    public FirebaseUser getCurrentUser() {
        return firebaseAuth == null ? null : firebaseAuth.getCurrentUser();
    }

    public Task<AuthResult> signIn(String email, String password) {
        if (firebaseAuth == null) {
            throw new IllegalStateException("Firebase is not configured for this build.");
        }
        return firebaseAuth.signInWithEmailAndPassword(email, password);
    }

    public void signOut() {
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }
    }

    public void addAuthStateListener(FirebaseAuth.AuthStateListener listener) {
        if (firebaseAuth != null) {
            firebaseAuth.addAuthStateListener(listener);
        }
    }

    public void removeAuthStateListener(FirebaseAuth.AuthStateListener listener) {
        if (firebaseAuth != null) {
            firebaseAuth.removeAuthStateListener(listener);
        }
    }
}
