package th.ac.vu.classflow.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.DataValidationException;
import th.ac.vu.classflow.data.repository.EnrollmentRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.StudentProgressRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.ui.activity.StudentDetailActivity;
import th.ac.vu.classflow.ui.adapter.StudentAdapter;
import th.ac.vu.classflow.ui.dialog.EnrollmentDialogFragment;
import th.ac.vu.classflow.ui.dialog.StudentEditorDialogFragment;
import th.ac.vu.classflow.ui.model.EnrollmentCardItem;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.StudentListItem;

public final class StudentsFragment extends Fragment {

    private static final String TRACK_ALL = "ALL";

    private final StudentRepository studentRepository = new StudentRepository();
    private final EnrollmentRepository enrollmentRepository = new EnrollmentRepository();
    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final StudentProgressRepository progressRepository = new StudentProgressRepository();
    private final List<StudentListItem> allItems = new ArrayList<>();
    private final List<ClassOption> classOptions = new ArrayList<>();

    private View root;
    private RecyclerView list;
    private View loading;
    private TextView empty;
    private View error;
    private TextView errorMessage;
    private TextInputEditText search;
    private AutoCompleteTextView classFilter;
    private StudentAdapter adapter;
    private boolean activeFilter = true;
    private String trackFilter = TRACK_ALL;
    private String statusFilter = ProgressPresentation.FILTER_ALL;
    @Nullable private String classIdFilter;
    private int loadGeneration;
    private boolean resumedOnce;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_students, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        root = view;
        bind(view);
        configureList();
        configureActions();
        getParentFragmentManager().setFragmentResultListener(
                StudentEditorDialogFragment.RESULT_STUDENT_CHANGED, this,
                (key, result) -> loadStudents());
        getParentFragmentManager().setFragmentResultListener(
                EnrollmentDialogFragment.RESULT_ENROLLMENT_CHANGED, this,
                (key, result) -> loadStudents());
        loadStudents();
    }

    @Override public void onDestroyView() {
        loadGeneration++;
        resumedOnce = false;
        root = null;
        super.onDestroyView();
    }

    @Override public void onResume() {
        super.onResume();
        if (resumedOnce && root != null) loadStudents();
        resumedOnce = true;
    }

    private void bind(View view) {
        list = view.findViewById(R.id.student_list);
        loading = view.findViewById(R.id.student_loading);
        empty = view.findViewById(R.id.student_empty);
        error = view.findViewById(R.id.student_error);
        errorMessage = view.findViewById(R.id.student_error_message);
        search = view.findViewById(R.id.student_search);
        classFilter = view.findViewById(R.id.student_class_filter);
    }

    private void configureList() {
        adapter = new StudentAdapter(new StudentAdapter.Actions() {
            @Override public void onOpen(StudentListItem item) { openDetail(item); }
            @Override public void onEdit(StudentListItem item) { openEditor(item); }
            @Override public void onManageEnrollment(StudentListItem item) { openEnrollments(item); }
            @Override public void onArchive(StudentListItem item) { confirmArchive(item); }
            @Override public void onRestore(StudentListItem item) { restore(item); }
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
    }

    private void configureActions() {
        root.findViewById(R.id.add_student).setOnClickListener(ignored ->
                showDialog(StudentEditorDialogFragment.newCreate(), "student_create"));
        root.findViewById(R.id.student_retry).setOnClickListener(ignored -> loadStudents());
        root.findViewById(R.id.student_filter_active).setOnClickListener(ignored -> {
            if (!activeFilter) { activeFilter = true; loadStudents(); }
        });
        root.findViewById(R.id.student_filter_archived).setOnClickListener(ignored -> {
            if (activeFilter) { activeFilter = false; loadStudents(); }
        });
        root.findViewById(R.id.student_track_all).setOnClickListener(ignored -> setTrack(TRACK_ALL));
        root.findViewById(R.id.student_track_ss1).setOnClickListener(ignored -> setTrack("ss1"));
        root.findViewById(R.id.student_track_ss2).setOnClickListener(ignored -> setTrack("ss2"));
        bindStatusFilter(R.id.student_status_all, ProgressPresentation.FILTER_ALL);
        bindStatusFilter(R.id.student_status_not_recorded, ProgressPresentation.NOT_RECORDED);
        bindStatusFilter(R.id.student_status_not_started, StudentProgress.STATUS_NOT_STARTED);
        bindStatusFilter(R.id.student_status_on_track, StudentProgress.STATUS_ON_TRACK);
        bindStatusFilter(R.id.student_status_needs_attention, StudentProgress.STATUS_NEEDS_ATTENTION);
        bindStatusFilter(R.id.student_status_blocked, StudentProgress.STATUS_BLOCKED);
        bindStatusFilter(R.id.student_status_completed, StudentProgress.STATUS_COMPLETED);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void bindStatusFilter(int viewId, String value) {
        root.findViewById(viewId).setOnClickListener(ignored -> {
            statusFilter = value;
            applyFilters();
        });
    }

    private void setTrack(String value) {
        trackFilter = value;
        if (classIdFilter != null && !classMatchesTrack(classIdFilter, value)) {
            classIdFilter = null;
            classFilter.setText(getString(R.string.all_classes), false);
        }
        applyFilters();
    }

    private void loadStudents() {
        int request = ++loadGeneration;
        showLoading();
        studentRepository.loadStudents(activeFilter).addOnSuccessListener(students -> {
            if (!usable(request)) return;
            if (students.isEmpty()) {
                allItems.clear();
                configureClassFilter();
                applyFilters();
                return;
            }
            List<Task<StudentListItem>> tasks = new ArrayList<>();
            for (Student student : students) tasks.add(resolveStudent(student));
            Tasks.whenAllComplete(tasks).addOnCompleteListener(ignored -> {
                if (!usable(request)) return;
                List<StudentListItem> resolved = new ArrayList<>();
                for (Task<StudentListItem> task : tasks) {
                    if (!task.isSuccessful()) { showError(task.getException()); return; }
                    resolved.add(task.getResult());
                }
                resolved.sort(Comparator.comparing(item -> item.getStudent().getName(),
                        String.CASE_INSENSITIVE_ORDER));
                allItems.clear();
                allItems.addAll(resolved);
                configureClassFilter();
                applyFilters();
            });
        }).addOnFailureListener(failure -> {
            if (usable(request)) showError(failure);
        });
    }

    private Task<StudentListItem> resolveStudent(Student student) {
        return enrollmentRepository.loadForStudent(student.getStudentId())
                .continueWithTask(enrollmentTask -> {
                    List<Enrollment> enrollments = enrollmentTask.getResult();
                    if (enrollments.isEmpty()) {
                        return Tasks.forResult(new StudentListItem(student, new ArrayList<>()));
                    }
                    List<Task<EnrollmentCardItem>> tasks = new ArrayList<>();
                    for (Enrollment enrollment : enrollments) {
                        tasks.add(classRepository.loadClass(enrollment.getClassId())
                                .continueWithTask(classTask -> {
                                    ClassOffering selectedClass = classTask.getResult();
                                    if (!selectedClass.getTrackId().equals(enrollment.getTrackId())) {
                                        throw new DataValidationException("Enrollment Track does not match its Class.");
                                    }
                                    return trackRepository.loadTrack(enrollment.getTrackId())
                                            .continueWith(trackTask -> new EnrollmentCardItem(
                                                    enrollment, selectedClass, trackTask.getResult()));
                                }));
                    }
                    return Tasks.whenAllComplete(tasks).continueWithTask(done -> {
                        List<EnrollmentCardItem> values = new ArrayList<>();
                        for (Task<EnrollmentCardItem> task : tasks) values.add(task.getResult());
                        List<Task<StudentProgress>> progressTasks = new ArrayList<>();
                        List<EnrollmentCardItem> activeCards = new ArrayList<>();
                        for (EnrollmentCardItem value : values) {
                            if (Enrollment.STATUS_ACTIVE.equals(value.getEnrollment().getStatus())) {
                                activeCards.add(value);
                                progressTasks.add(progressRepository.loadOne(
                                        value.getEnrollment().getClassId(), student.getStudentId(),
                                        weekId(value.getClassOffering().getCurrentWeek())));
                            }
                        }
                        if (progressTasks.isEmpty()) {
                            return Tasks.forResult(new StudentListItem(student, values));
                        }
                        return Tasks.whenAllComplete(progressTasks).continueWith(progressDone -> {
                            Map<String, StudentProgress> progressByClass = new LinkedHashMap<>();
                            for (int index = 0; index < progressTasks.size(); index++) {
                                StudentProgress progress = progressTasks.get(index).getResult();
                                if (progress != null) {
                                    progressByClass.put(activeCards.get(index).getEnrollment().getClassId(), progress);
                                }
                            }
                            return new StudentListItem(student, values, progressByClass);
                        });
                    });
                });
    }

    private void configureClassFilter() {
        Map<String, ClassOption> unique = new LinkedHashMap<>();
        for (StudentListItem student : allItems) {
            for (EnrollmentCardItem enrollment : student.getActiveEnrollments()) {
                unique.put(enrollment.getClassOffering().getClassId(),
                        new ClassOption(enrollment.getClassOffering().getClassId(),
                                enrollment.getClassOffering().getName(), enrollment.getTrack().getCode(),
                                enrollment.getTrack().getTrackId()));
            }
        }
        classOptions.clear();
        classOptions.addAll(unique.values());
        classOptions.sort(Comparator.comparing(option -> option.label, String.CASE_INSENSITIVE_ORDER));
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.all_classes));
        for (ClassOption option : classOptions) labels.add(option.label);
        classFilter.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));
        classFilter.setThreshold(0);
        classFilter.setOnClickListener(ignored -> {
            classFilter.setText("", false);
            classFilter.showDropDown();
        });
        classFilter.setOnDismissListener(() -> {
            if (classFilter.getText().length() == 0) {
                classFilter.setText(classIdFilter == null ? getString(R.string.all_classes)
                        : selectedClassLabel(), false);
            }
        });
        int selectedIndex = 0;
        if (classIdFilter != null) {
            for (int i = 0; i < classOptions.size(); i++) {
                if (classIdFilter.equals(classOptions.get(i).classId)) selectedIndex = i + 1;
            }
            if (selectedIndex == 0) classIdFilter = null;
        }
        classFilter.setText(labels.get(selectedIndex), false);
        classFilter.setOnItemClickListener((parent, view, position, id) -> {
            classIdFilter = position == 0 ? null : classOptions.get(position - 1).classId;
            applyFilters();
        });
    }

    private void applyFilters() {
        if (root == null || adapter == null) return;
        String query = search.getText() == null ? "" : search.getText().toString()
                .trim().toLowerCase(Locale.ROOT);
        List<StudentListItem> visible = new ArrayList<>();
        for (StudentListItem item : allItems) {
            Student student = item.getStudent();
            String identity = (student.getStudentId() + " " + student.getName() + " "
                    + (student.getNickname() == null ? "" : student.getNickname()))
                    .toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !identity.contains(query)) continue;
            if (!TRACK_ALL.equals(trackFilter) && !hasTrack(item, trackFilter)) continue;
            if (classIdFilter != null && !hasClass(item, classIdFilter)) continue;
            if (!ProgressPresentation.FILTER_ALL.equals(statusFilter)
                    && !hasStatus(item, statusFilter)) continue;
            visible.add(item);
        }
        adapter.submitList(visible);
        loading.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        boolean noResults = visible.isEmpty();
        list.setVisibility(noResults ? View.GONE : View.VISIBLE);
        empty.setVisibility(noResults ? View.VISIBLE : View.GONE);
        if (noResults) {
            boolean filtered = !query.isEmpty() || !TRACK_ALL.equals(trackFilter)
                    || classIdFilter != null || !ProgressPresentation.FILTER_ALL.equals(statusFilter);
            empty.setText(filtered ? R.string.no_matching_students
                    : activeFilter ? R.string.no_active_students : R.string.no_archived_students);
        }
    }

    private boolean hasStatus(StudentListItem item, String expectedStatus) {
        for (EnrollmentCardItem value : item.getActiveEnrollments()) {
            if (classIdFilter != null && !classIdFilter.equals(value.getEnrollment().getClassId())) continue;
            if (!TRACK_ALL.equals(trackFilter)
                    && !trackFilter.equals(value.getEnrollment().getTrackId())) continue;
            StudentProgress progress = item.getCurrentProgress(value.getEnrollment().getClassId());
            String actual = progress == null ? ProgressPresentation.NOT_RECORDED
                    : progress.getOverallStatus();
            if (expectedStatus.equals(actual)) return true;
        }
        return false;
    }

    private String weekId(int weekNumber) {
        return String.format(Locale.US, "W%02d", weekNumber);
    }

    private boolean hasTrack(StudentListItem item, String trackId) {
        for (EnrollmentCardItem value : item.getActiveEnrollments()) {
            if (trackId.equals(value.getEnrollment().getTrackId())) return true;
        }
        return false;
    }

    private boolean hasClass(StudentListItem item, String classId) {
        for (EnrollmentCardItem value : item.getActiveEnrollments()) {
            if (classId.equals(value.getEnrollment().getClassId())) return true;
        }
        return false;
    }

    private boolean classMatchesTrack(String classId, String trackId) {
        if (TRACK_ALL.equals(trackId)) return true;
        for (ClassOption option : classOptions) {
            if (classId.equals(option.classId)) return trackId.equals(option.trackId);
        }
        return false;
    }

    private String selectedClassLabel() {
        for (ClassOption option : classOptions) {
            if (option.classId.equals(classIdFilter)) return option.label;
        }
        return getString(R.string.all_classes);
    }

    private void openDetail(StudentListItem item) {
        Intent intent = new Intent(requireContext(), StudentDetailActivity.class);
        intent.putExtra(StudentDetailActivity.EXTRA_STUDENT_ID, item.getStudent().getStudentId());
        startActivity(intent);
    }

    private void openEditor(StudentListItem item) {
        showDialog(StudentEditorDialogFragment.newEdit(item.getStudent().getStudentId()),
                "student_edit");
    }

    private void openEnrollments(StudentListItem item) {
        showDialog(EnrollmentDialogFragment.newInstance(item.getStudent().getStudentId()),
                "student_enrollments");
    }

    private void showDialog(androidx.fragment.app.DialogFragment dialog, String tag) {
        if (getParentFragmentManager().findFragmentByTag(tag) == null) {
            dialog.show(getParentFragmentManager(), tag);
        }
    }

    private void confirmArchive(StudentListItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.archive_student_title)
                .setMessage(R.string.archive_student_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.archive, (dialog, which) -> changeActive(item, false))
                .show();
    }

    private void restore(StudentListItem item) { changeActive(item, true); }

    private void changeActive(StudentListItem item, boolean active) {
        String id = item.getStudent().getStudentId();
        adapter.setBusy(id);
        Task<Void> task = active ? studentRepository.restoreStudent(id)
                : studentRepository.archiveStudent(id);
        task.addOnSuccessListener(ignored -> {
            if (!isAdded() || root == null) return;
            adapter.setBusy(null);
            Snackbar.make(root, active ? R.string.student_restored : R.string.student_archived,
                    Snackbar.LENGTH_LONG).show();
            loadStudents();
        }).addOnFailureListener(failure -> {
            if (!isAdded() || root == null) return;
            adapter.setBusy(null);
            Snackbar.make(root, FirestoreErrorMessages.forException(failure),
                    Snackbar.LENGTH_LONG).show();
        });
    }

    private void showLoading() {
        loading.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
    }

    private void showError(Exception failure) {
        loading.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        errorMessage.setText(FirestoreErrorMessages.forException(failure));
    }

    private boolean usable(int request) { return isAdded() && root != null && request == loadGeneration; }

    private static final class ClassOption {
        final String classId;
        final String label;
        final String trackId;
        ClassOption(String classId, String name, String trackCode, String trackId) {
            this.classId = classId;
            this.label = trackCode + " · " + name;
            this.trackId = trackId;
        }
    }
}
