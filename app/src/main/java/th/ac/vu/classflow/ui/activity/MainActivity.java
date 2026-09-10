package th.ac.vu.classflow.ui.activity;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.bootstrap.CurriculumBootstrapper;
import th.ac.vu.classflow.data.firebase.AuthService;
import th.ac.vu.classflow.ui.fragment.HomeFragment;
import th.ac.vu.classflow.ui.fragment.ProjectsFragment;
import th.ac.vu.classflow.ui.fragment.StudentsFragment;

public final class MainActivity extends ProtectedActivity {

    public enum CurriculumState {
        NOT_STARTED,
        LOADING,
        READY,
        ERROR
    }

    private static final String STATE_CURRENT_TAG = "state_current_tag";
    private static final String TAG_HOME = "home";
    private static final String TAG_STUDENTS = "students";
    private static final String TAG_PROJECTS = "projects";

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigation;
    private String currentTag = TAG_HOME;
    private CurriculumState curriculumState = CurriculumState.NOT_STARTED;
    @Nullable
    private Exception curriculumError;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.main_toolbar);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        View mainRoot = findViewById(R.id.main_root);

        applySystemBarInsets(mainRoot);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                showSignOutConfirmation();
                return true;
            }
            return false;
        });

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                showDestination(TAG_HOME);
                return true;
            }
            if (itemId == R.id.nav_students) {
                showDestination(TAG_STUDENTS);
                return true;
            }
            if (itemId == R.id.nav_projects) {
                showDestination(TAG_PROJECTS);
                return true;
            }
            return false;
        });

        if (savedInstanceState != null) {
            currentTag = savedInstanceState.getString(STATE_CURRENT_TAG, TAG_HOME);
        }

        bottomNavigation.getMenu().findItem(menuIdFor(currentTag)).setChecked(true);
        showDestination(currentTag);
        startCurriculumBootstrap();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_CURRENT_TAG, currentTag);
        super.onSaveInstanceState(outState);
    }

    private void showDestination(String targetTag) {
        FragmentManager manager = getSupportFragmentManager();
        Fragment current = manager.findFragmentByTag(currentTag);
        Fragment target = manager.findFragmentByTag(targetTag);

        FragmentTransaction transaction = manager.beginTransaction();
        if (current != null && current != target) {
            transaction.hide(current);
        }

        if (target == null) {
            target = createFragment(targetTag);
            transaction.add(R.id.fragment_container, target, targetTag);
        } else {
            transaction.show(target);
        }

        transaction.commitNow();
        currentTag = targetTag;
        if (TAG_HOME.equals(targetTag)) {
            notifyHomeOfCurriculumState();
        }
    }

    private Fragment createFragment(String tag) {
        if (TAG_STUDENTS.equals(tag)) {
            return new StudentsFragment();
        }
        if (TAG_PROJECTS.equals(tag)) {
            return new ProjectsFragment();
        }
        return new HomeFragment();
    }

    private int menuIdFor(String tag) {
        if (TAG_STUDENTS.equals(tag)) {
            return R.id.nav_students;
        }
        if (TAG_PROJECTS.equals(tag)) {
            return R.id.nav_projects;
        }
        return R.id.nav_home;
    }

    private void applySystemBarInsets(View mainRoot) {
        int rootPaddingLeft = mainRoot.getPaddingLeft();
        int rootPaddingTop = mainRoot.getPaddingTop();
        int rootPaddingRight = mainRoot.getPaddingRight();
        int rootPaddingBottom = mainRoot.getPaddingBottom();
        int navigationPaddingLeft = bottomNavigation.getPaddingLeft();
        int navigationPaddingTop = bottomNavigation.getPaddingTop();
        int navigationPaddingRight = bottomNavigation.getPaddingRight();
        int navigationPaddingBottom = bottomNavigation.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            int safeLeft = Math.max(systemBars.left, displayCutout.left);
            int safeTop = Math.max(systemBars.top, displayCutout.top);
            int safeRight = Math.max(systemBars.right, displayCutout.right);
            int safeBottom = Math.max(systemBars.bottom, displayCutout.bottom);

            view.setPadding(
                    rootPaddingLeft + safeLeft,
                    rootPaddingTop + safeTop,
                    rootPaddingRight + safeRight,
                    rootPaddingBottom
            );
            bottomNavigation.setPadding(
                    navigationPaddingLeft,
                    navigationPaddingTop,
                    navigationPaddingRight,
                    navigationPaddingBottom + safeBottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(mainRoot);
    }

    private void showSignOutConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sign_out_confirmation_title)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sign_out, (dialog, which) -> performSignOut())
                .show();
    }

    private void performSignOut() {
        AuthService.getInstance().signOut();
        navigateToLoginAndClearTask();
    }

    public CurriculumState getCurriculumState() {
        return curriculumState;
    }

    @Nullable
    public Exception getCurriculumError() {
        return curriculumError;
    }

    public void retryCurriculumBootstrap() {
        if (curriculumState != CurriculumState.LOADING) {
            startCurriculumBootstrap();
        }
    }

    private void startCurriculumBootstrap() {
        if (AuthService.getInstance().getCurrentUser() == null) {
            return;
        }
        curriculumState = CurriculumState.LOADING;
        curriculumError = null;
        notifyHomeOfCurriculumState();

        new CurriculumBootstrapper().bootstrapMissingCurriculum()
                .addOnSuccessListener(ignored -> {
                    curriculumState = CurriculumState.READY;
                    curriculumError = null;
                    notifyHomeOfCurriculumState();
                })
                .addOnFailureListener(failure -> {
                    curriculumState = CurriculumState.ERROR;
                    curriculumError = failure;
                    notifyHomeOfCurriculumState();
                });
    }

    private void notifyHomeOfCurriculumState() {
        Fragment home = getSupportFragmentManager().findFragmentByTag(TAG_HOME);
        if (home instanceof HomeFragment) {
            ((HomeFragment) home).onCurriculumBootstrapState(curriculumState, curriculumError);
        }
    }
}
