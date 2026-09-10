package th.ac.vu.classflow.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.bootstrap.CurriculumCatalog;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.ui.adapter.ManageClassAdapter;
import th.ac.vu.classflow.ui.dialog.ClassEditorDialogFragment;
import th.ac.vu.classflow.ui.model.ClassCardItem;
import th.ac.vu.classflow.ui.util.SystemBarInsets;

public final class ManageClassesActivity extends ProtectedActivity {

    private static final String STATE_FILTER = "manage_classes_filter";

    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();

    private View root;
    private RecyclerView classList;
    private View loading;
    private TextView empty;
    private View error;
    private TextView errorMessage;
    private ManageClassAdapter adapter;
    private int loadGeneration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_classes);
        root = findViewById(R.id.manage_classes_root);
        SystemBarInsets.applyToRoot(root);

        MaterialToolbar toolbar = findViewById(R.id.manage_classes_toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.back);
        toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());

        bindViews();
        configureList();
        configureActions();

        String initialFilter = savedInstanceState == null
                ? ClassOffering.STATUS_ACTIVE
                : savedInstanceState.getString(STATE_FILTER, ClassOffering.STATUS_ACTIVE);
        adapter.setFilter(initialFilter);
        checkFilterChip(initialFilter);

        getSupportFragmentManager().setFragmentResultListener(
                ClassEditorDialogFragment.RESULT_CLASS_CHANGED,
                this,
                (requestKey, result) -> loadClasses()
        );
        loadClasses();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_FILTER, adapter.getFilter());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        loadGeneration++;
        super.onDestroy();
    }

    private void bindViews() {
        classList = findViewById(R.id.manage_class_list);
        loading = findViewById(R.id.manage_class_loading);
        empty = findViewById(R.id.manage_class_empty);
        error = findViewById(R.id.manage_class_error);
        errorMessage = findViewById(R.id.manage_class_error_message);
    }

    private void configureList() {
        adapter = new ManageClassAdapter(new ManageClassAdapter.Actions() {
            @Override
            public void onEdit(ClassCardItem item) {
                openEditor(ClassEditorDialogFragment.newEdit(
                        item.getClassOffering().getClassId()), "class_editor_edit");
            }

            @Override
            public void onLifecycleChange(ClassCardItem item, String targetStatus) {
                requestLifecycleChange(item, targetStatus);
            }
        });
        classList.setLayoutManager(new LinearLayoutManager(this));
        classList.setAdapter(adapter);
    }

    private void configureActions() {
        findViewById(R.id.manage_add_class).setOnClickListener(ignored ->
                openEditor(ClassEditorDialogFragment.newCreate(), "class_editor_create"));
        findViewById(R.id.manage_class_retry).setOnClickListener(ignored -> loadClasses());

        View.OnClickListener filterListener = chip -> {
            adapter.setFilter(filterForChip(chip.getId()));
            renderFilteredState();
        };
        findViewById(R.id.filter_all).setOnClickListener(filterListener);
        findViewById(R.id.filter_active).setOnClickListener(filterListener);
        findViewById(R.id.filter_planned).setOnClickListener(filterListener);
        findViewById(R.id.filter_completed).setOnClickListener(filterListener);
        findViewById(R.id.filter_archived).setOnClickListener(filterListener);
    }

    private void openEditor(ClassEditorDialogFragment editor, String tag) {
        if (getSupportFragmentManager().findFragmentByTag(tag) == null) {
            editor.show(getSupportFragmentManager(), tag);
        }
    }

    private void loadClasses() {
        int generation = ++loadGeneration;
        showLoading();
        classRepository.loadAllClasses()
                .addOnSuccessListener(classes -> {
                    if (!isCurrent(generation)) {
                        return;
                    }
                    if (classes.isEmpty()) {
                        adapter.submitList(new ArrayList<>());
                        renderFilteredState();
                        return;
                    }
                    loadClassCards(classes, generation);
                })
                .addOnFailureListener(failure -> {
                    if (isCurrent(generation)) {
                        showError(FirestoreErrorMessages.forException(failure));
                    }
                });
    }

    private void loadClassCards(List<ClassOffering> classes, int generation) {
        List<Task<ClassCardItem>> tasks = new ArrayList<>();
        for (ClassOffering classOffering : classes) {
            Task<ClassCardItem> task = trackRepository.loadTrack(classOffering.getTrackId())
                    .continueWithTask(trackTask -> {
                        Track track = trackTask.getResult();
                        String weekId = CurriculumCatalog.weekId(classOffering.getCurrentWeek());
                        return trackRepository.loadWeek(track.getTrackId(), weekId)
                                .continueWith(weekTask -> new ClassCardItem(
                                        classOffering, track, weekTask.getResult()));
                    });
            tasks.add(task);
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener(ignored -> {
            if (!isCurrent(generation)) {
                return;
            }
            List<ClassCardItem> items = new ArrayList<>();
            for (Task<ClassCardItem> task : tasks) {
                if (!task.isSuccessful()) {
                    showError(FirestoreErrorMessages.forException(task.getException()));
                    return;
                }
                items.add(task.getResult());
            }
            adapter.submitList(items);
            renderFilteredState();
        });
    }

    private void requestLifecycleChange(ClassCardItem item, String targetStatus) {
        if (ClassOffering.STATUS_ARCHIVED.equals(targetStatus)
                || ClassOffering.STATUS_COMPLETED.equals(targetStatus)) {
            int title = ClassOffering.STATUS_ARCHIVED.equals(targetStatus)
                    ? R.string.archive_class_title : R.string.mark_completed_title;
            int action = ClassOffering.STATUS_ARCHIVED.equals(targetStatus)
                    ? R.string.archive : R.string.mark_completed;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(action, (dialog, which) ->
                            performLifecycleChange(item, targetStatus))
                    .show();
        } else {
            performLifecycleChange(item, targetStatus);
        }
    }

    private void performLifecycleChange(ClassCardItem item, String targetStatus) {
        String classId = item.getClassOffering().getClassId();
        adapter.setClassBusy(classId, true);
        classRepository.updateLifecycleStatus(classId, targetStatus)
                .addOnSuccessListener(ignored -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    adapter.updateStatus(classId, targetStatus);
                    renderFilteredState();
                    Snackbar.make(root, R.string.class_status_change_success,
                            Snackbar.LENGTH_SHORT).show();
                })
                .addOnFailureListener(failure -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    adapter.setClassBusy(classId, false);
                    Snackbar.make(root, FirestoreErrorMessages.forException(failure),
                            Snackbar.LENGTH_LONG).show();
                });
    }

    private String filterForChip(int chipId) {
        if (chipId == R.id.filter_all) {
            return ManageClassAdapter.FILTER_ALL;
        }
        if (chipId == R.id.filter_planned) {
            return ClassOffering.STATUS_PLANNED;
        }
        if (chipId == R.id.filter_completed) {
            return ClassOffering.STATUS_COMPLETED;
        }
        if (chipId == R.id.filter_archived) {
            return ClassOffering.STATUS_ARCHIVED;
        }
        return ClassOffering.STATUS_ACTIVE;
    }

    private void checkFilterChip(String filter) {
        int chipId;
        if (ManageClassAdapter.FILTER_ALL.equals(filter)) {
            chipId = R.id.filter_all;
        } else if (ClassOffering.STATUS_PLANNED.equals(filter)) {
            chipId = R.id.filter_planned;
        } else if (ClassOffering.STATUS_COMPLETED.equals(filter)) {
            chipId = R.id.filter_completed;
        } else if (ClassOffering.STATUS_ARCHIVED.equals(filter)) {
            chipId = R.id.filter_archived;
        } else {
            chipId = R.id.filter_active;
        }
        ((com.google.android.material.chip.Chip) findViewById(chipId)).setChecked(true);
    }

    private void renderFilteredState() {
        loading.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        boolean isEmpty = adapter.isEmpty();
        classList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        empty.setText(emptyMessageForFilter(adapter.getFilter()));
    }

    private int emptyMessageForFilter(String filter) {
        if (ManageClassAdapter.FILTER_ALL.equals(filter)) {
            return R.string.no_classes_yet;
        }
        if (ClassOffering.STATUS_PLANNED.equals(filter)) {
            return R.string.no_planned_classes;
        }
        if (ClassOffering.STATUS_COMPLETED.equals(filter)) {
            return R.string.no_completed_classes;
        }
        if (ClassOffering.STATUS_ARCHIVED.equals(filter)) {
            return R.string.no_archived_classes;
        }
        return R.string.no_active_classes_short;
    }

    private void showLoading() {
        loading.setVisibility(View.VISIBLE);
        classList.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        classList.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        errorMessage.setText(message);
    }

    private boolean isCurrent(int generation) {
        return !isFinishing() && !isDestroyed() && generation == loadGeneration;
    }
}
