package th.ac.vu.classflow.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.AuthService;

public final class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailInputLayout;
    private TextInputLayout passwordInputLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextView loginError;
    private MaterialButton signInButton;
    private CircularProgressIndicator loginProgress;
    private boolean signInInProgress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInputLayout = findViewById(R.id.email_input_layout);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        loginError = findViewById(R.id.login_error);
        signInButton = findViewById(R.id.sign_in_button);
        loginProgress = findViewById(R.id.login_progress);

        signInButton.setOnClickListener(view -> attemptSignIn());
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSignIn();
                return true;
            }
            return false;
        });

        if (!AuthService.getInstance().isConfigured()) {
            showError(getString(R.string.firebase_setup_required));
            signInButton.setEnabled(false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (AuthService.getInstance().getCurrentUser() != null) {
            navigateToMainAndClearTask();
        }
    }

    private void attemptSignIn() {
        if (signInInProgress || !AuthService.getInstance().isConfigured()) {
            return;
        }

        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
        hideError();

        String email = textOf(emailInput).trim();
        String password = textOf(passwordInput); // Password must never be trimmed.

        boolean valid = true;
        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError(getString(R.string.email_required));
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError(getString(R.string.email_invalid));
            valid = false;
        }

        if (password.isEmpty()) {
            passwordInputLayout.setError(getString(R.string.password_required));
            valid = false;
        }

        if (!valid) {
            return;
        }

        setLoading(true);
        AuthService.getInstance().signIn(email, password).addOnCompleteListener(this, task -> {
            setLoading(false);
            if (task.isSuccessful()) {
                navigateToMainAndClearTask();
                return;
            }
            showError(messageFor(task.getException()));
        });
    }

    private String messageFor(@Nullable Exception exception) {
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return getString(R.string.auth_invalid_credentials);
        }
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return getString(R.string.auth_user_not_found);
        }
        if (exception instanceof FirebaseNetworkException) {
            return getString(R.string.auth_network_error);
        }
        if (exception instanceof FirebaseTooManyRequestsException) {
            return getString(R.string.auth_too_many_requests);
        }
        return getString(R.string.auth_unknown_error);
    }

    private void setLoading(boolean loading) {
        signInInProgress = loading;
        signInButton.setEnabled(!loading);
        signInButton.setText(loading ? R.string.signing_in : R.string.sign_in);
        loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        loginError.setText(message);
        loginError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        loginError.setText(null);
        loginError.setVisibility(View.GONE);
    }

    private void navigateToMainAndClearTask() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
