package th.ac.vu.classflow.ui.activity;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import th.ac.vu.classflow.data.firebase.AuthService;

public abstract class ProtectedActivity extends AppCompatActivity {

    private boolean redirectingToLogin;
    private final FirebaseAuth.AuthStateListener authStateListener = firebaseAuth -> {
        if (firebaseAuth.getCurrentUser() == null) {
            navigateToLoginAndClearTask();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        AuthService.getInstance().addAuthStateListener(authStateListener);
        if (!AuthService.getInstance().isConfigured()
                || AuthService.getInstance().getCurrentUser() == null) {
            navigateToLoginAndClearTask();
        }
    }

    @Override
    protected void onStop() {
        AuthService.getInstance().removeAuthStateListener(authStateListener);
        super.onStop();
    }

    protected final void navigateToLoginAndClearTask() {
        if (redirectingToLogin) {
            return;
        }
        redirectingToLogin = true;
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
