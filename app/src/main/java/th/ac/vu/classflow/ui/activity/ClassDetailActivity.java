package th.ac.vu.classflow.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.data.repository.EnrollmentRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.StudentProgressRepository;
import th.ac.vu.classflow.ui.adapter.CurriculumWeekAdapter;
import th.ac.vu.classflow.ui.adapter.StudentStatusAdapter;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.StudentStatusItem;
import th.ac.vu.classflow.ui.util.SystemBarInsets;

public final class ClassDetailActivity extends ProtectedActivity {

    public static final String EXTRA_CLASS_ID = "th.ac.vu.classflow.extra.CLASS_ID";

    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final EnrollmentRepository enrollmentRepository = new EnrollmentRepository();
    private final StudentRepository studentRepository = new StudentRepository();
    private final StudentProgressRepository progressRepository = new StudentProgressRepository();
    private final List<CurriculumWeek> weeks = new ArrayList<>();
    private final List<StudentStatusItem> allStudents = new ArrayList<>();

    private View content;
    private View loading;
    private View error;
    private TextView errorMessage;
    private TextView className;
    private TextView trackName;
    private TextView classStatus;
    private TextView currentWeekTitle;
    private AutoCompleteTextView weekSelector;
    private MaterialButton saveCurrentWeek;
    private CurriculumWeekAdapter weekAdapter;
    private StudentStatusAdapter studentAdapter;
    private TextView studentsEmpty;
    private String studentStatusFilter = ProgressPresentation.FILTER_ALL;
    private String classId;
    private ClassOffering classOffering;
    private Track track;
    private CurriculumWeek selectedWeek;
    private int loadGeneration;
    private boolean writeInFlight;
    private boolean resumedOnce;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_detail);
        SystemBarInsets.applyToRoot(findViewById(R.id.class_detail_root));

        MaterialToolbar toolbar = findViewById(R.id.class_detail_toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.back);
        toolbar.setNavigationOnClickListener(view -> finish());

        bindViews();
        weekAdapter = new CurriculumWeekAdapter(this::openWeek);
        RecyclerView weekList = findViewById(R.id.curriculum_week_list);
        weekList.setLayoutManager(new LinearLayoutManager(this));
        weekList.setAdapter(weekAdapter);
        studentAdapter = new StudentStatusAdapter(this::openStudent);
        RecyclerView studentList = findViewById(R.id.class_detail_student_list);
        studentList.setLayoutManager(new LinearLayoutManager(this));
        studentList.setAdapter(studentAdapter);
        studentsEmpty = findViewById(R.id.class_detail_students_empty);
        bindStatusFilters();

        findViewById(R.id.class_detail_retry).setOnClickListener(view -> loadDetails());
        saveCurrentWeek.setOnClickListener(view -> saveCurrentWeek());

        classId = getIntent().getStringExtra(EXTRA_CLASS_ID);
        if (classId == null || classId.trim().isEmpty()) {
            showError("The Class ID is missing.");
            return;
        }
        classId = classId.trim();
        loadDetails();
    }

    @Override
    protected void onDestroy() {
        loadGeneration++;
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (resumedOnce && classId != null && !writeInFlight) loadDetails();
        resumedOnce = true;
    }

    private void bindViews() {
        content = findViewById(R.id.class_detail_content);
        loading = findViewById(R.id.class_detail_loading);
        error = findViewById(R.id.class_detail_error);
        errorMessage = findViewById(R.id.class_detail_error_message);
        className = findViewById(R.id.class_detail_name);
        trackName = findViewById(R.id.class_detail_track);
        classStatus = findViewById(R.id.class_detail_status);
        currentWeekTitle = findViewById(R.id.class_detail_current_week);
        weekSelector = findViewById(R.id.class_detail_week_selector);
        saveCurrentWeek = findViewById(R.id.class_detail_save_week);
    }

    private void loadDetails() {
        int generation = ++loadGeneration;
        showLoading();
        classRepository.loadClass(classId)
                .addOnSuccessListener(result -> {
                    if (!isCurrent(generation)) {
                        return;
                    }
                    classOffering = result;
                    trackRepository.loadTrack(result.getTrackId())
                            .addOnSuccessListener(trackResult -> {
                                if (!isCurrent(generation)) {
                                    return;
                                }
                                track = trackResult;
                                trackRepository.loadWeeks(trackResult.getTrackId())
                                        .addOnSuccessListener(weekResult -> {
                                            if (!isCurrent(generation)) {
                                                return;
                                            }
                                            if (!isCompleteCurriculum(trackResult, weekResult)) {
                                                showError("Curriculum data is incomplete or inconsistent for this Track.");
                                                return;
                                            }
                                            weeks.clear();
                                            weeks.addAll(weekResult);
                                            bindContent(generation);
                                        })
                                        .addOnFailureListener(failure -> showFailure(generation, failure));
                            })
                            .addOnFailureListener(failure -> showFailure(generation, failure));
                })
                .addOnFailureListener(failure -> showFailure(generation, failure));
    }

    private void bindContent(int generation) {
        className.setText(classOffering.getName());
        trackName.setText(String.format(Locale.getDefault(), "%s · %s",
                track.getCode(), track.getName()));
        classStatus.setText(classOffering.getStatus());

        List<String> labels = new ArrayList<>();
        CurriculumWeek current = null;
        for (CurriculumWeek week : weeks) {
            labels.add(weekLabel(week));
            if (week.getWeekNumber() == classOffering.getCurrentWeek()) {
                current = week;
            }
        }
        if (current == null) {
            showError("The Class current Week does not exist in its Track curriculum.");
            return;
        }

        selectedWeek = current;
        weekSelector.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels));
        weekSelector.setText(weekLabel(current), false);
        weekSelector.setOnItemClickListener((parent, view, position, id) ->
                selectedWeek = weeks.get(position));
        renderCurrentWeek(current);
        weekAdapter.submitList(weeks, classOffering.getCurrentWeek());
        loadRoster(generation, current);
    }

    private void loadRoster(int generation, CurriculumWeek current) {
        enrollmentRepository.loadActiveForClass(classId).addOnSuccessListener(enrollments -> {
            if (!isCurrent(generation)) return;
            List<Task<StudentStatusItem>> tasks = new ArrayList<>();
            for (Enrollment enrollment : enrollments) {
                Task<th.ac.vu.classflow.data.model.Student> studentTask =
                        studentRepository.loadStudent(enrollment.getStudentId());
                Task<StudentProgress> progressTask = progressRepository.loadOne(classId,
                        enrollment.getStudentId(), current.getWeekId());
                tasks.add(Tasks.whenAllComplete(studentTask, progressTask).continueWith(done ->
                        new StudentStatusItem(studentTask.getResult(), enrollment,
                                progressTask.getResult())));
            }
            Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
                if (!isCurrent(generation)) return;
                allStudents.clear();
                for (Task<StudentStatusItem> task : tasks) {
                    if (!task.isSuccessful()) { showFailure(generation, task.getException()); return; }
                    allStudents.add(task.getResult());
                }
                allStudents.sort(java.util.Comparator.comparing(
                        item -> item.getStudent().getName(), String.CASE_INSENSITIVE_ORDER));
                applyStudentFilter();
                showContent();
            });
        }).addOnFailureListener(failure -> showFailure(generation, failure));
    }

    private void bindStatusFilters() {
        bindStatusFilter(R.id.class_students_status_all, ProgressPresentation.FILTER_ALL);
        bindStatusFilter(R.id.class_students_status_not_recorded, ProgressPresentation.NOT_RECORDED);
        bindStatusFilter(R.id.class_students_status_not_started, StudentProgress.STATUS_NOT_STARTED);
        bindStatusFilter(R.id.class_students_status_on_track, StudentProgress.STATUS_ON_TRACK);
        bindStatusFilter(R.id.class_students_status_needs_attention, StudentProgress.STATUS_NEEDS_ATTENTION);
        bindStatusFilter(R.id.class_students_status_blocked, StudentProgress.STATUS_BLOCKED);
        bindStatusFilter(R.id.class_students_status_completed, StudentProgress.STATUS_COMPLETED);
    }

    private void bindStatusFilter(int id, String status) {
        findViewById(id).setOnClickListener(view -> {
            studentStatusFilter = status;
            applyStudentFilter();
        });
    }

    private void applyStudentFilter() {
        List<StudentStatusItem> visible = new ArrayList<>();
        for (StudentStatusItem item : allStudents) {
            if (ProgressPresentation.FILTER_ALL.equals(studentStatusFilter)
                    || studentStatusFilter.equals(item.presentationStatus())) visible.add(item);
        }
        studentAdapter.submitList(visible);
        studentsEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        studentsEmpty.setText(allStudents.isEmpty() ? R.string.no_enrolled_students
                : R.string.no_students_for_status);
    }

    private void openStudent(StudentStatusItem item) {
        Intent intent = new Intent(this, StudentDetailActivity.class);
        intent.putExtra(StudentDetailActivity.EXTRA_STUDENT_ID, item.getStudent().getStudentId());
        intent.putExtra(StudentDetailActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(StudentDetailActivity.EXTRA_WEEK_ID,
                String.format(Locale.US, "W%02d", classOffering.getCurrentWeek()));
        startActivity(intent);
    }

    private void saveCurrentWeek() {
        if (writeInFlight || selectedWeek == null || classOffering == null) {
            return;
        }
        if (selectedWeek.getWeekNumber() == classOffering.getCurrentWeek()) {
            Snackbar.make(content, R.string.current_week_saved, Snackbar.LENGTH_SHORT).show();
            return;
        }
        writeInFlight = true;
        saveCurrentWeek.setEnabled(false);
        weekSelector.setEnabled(false);
        int newWeek = selectedWeek.getWeekNumber();
        classRepository.updateCurrentWeek(classId, newWeek)
                .addOnSuccessListener(ignored -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    classOffering.setCurrentWeek(newWeek);
                    CurriculumWeek savedWeek = findWeek(newWeek);
                    renderCurrentWeek(savedWeek);
                    weekAdapter.setCurrentWeek(newWeek);
                    writeInFlight = false;
                    saveCurrentWeek.setEnabled(true);
                    weekSelector.setEnabled(true);
                    Snackbar.make(content, R.string.current_week_saved, Snackbar.LENGTH_SHORT).show();
                    loadDetails();
                })
                .addOnFailureListener(failure -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    writeInFlight = false;
                    saveCurrentWeek.setEnabled(true);
                    weekSelector.setEnabled(true);
                    Snackbar.make(content, FirestoreErrorMessages.forException(failure),
                            Snackbar.LENGTH_LONG).show();
                });
    }

    private void openWeek(CurriculumWeek week) {
        Intent intent = new Intent(this, WeekDetailActivity.class);
        intent.putExtra(WeekDetailActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(WeekDetailActivity.EXTRA_WEEK_ID, week.getWeekId());
        startActivity(intent);
    }

    private void renderCurrentWeek(CurriculumWeek week) {
        currentWeekTitle.setText(getString(R.string.week_detail_format,
                week.getWeekNumber(), week.getTitle()));
    }

    private CurriculumWeek findWeek(int weekNumber) {
        for (CurriculumWeek week : weeks) {
            if (week.getWeekNumber() == weekNumber) {
                return week;
            }
        }
        throw new IllegalStateException("Validated curriculum Week is missing.");
    }

    private boolean isCompleteCurriculum(Track loadedTrack, List<CurriculumWeek> loadedWeeks) {
        if (loadedTrack.getTotalWeeks() < 1 || loadedWeeks.size() != loadedTrack.getTotalWeeks()) {
            return false;
        }
        Set<Integer> numbers = new HashSet<>();
        for (CurriculumWeek week : loadedWeeks) {
            if (week.getWeekNumber() < 1 || week.getWeekNumber() > loadedTrack.getTotalWeeks()
                    || !numbers.add(week.getWeekNumber())) {
                return false;
            }
        }
        return numbers.size() == loadedTrack.getTotalWeeks();
    }

    private void showFailure(int generation, Exception failure) {
        if (isCurrent(generation)) {
            showError(FirestoreErrorMessages.forException(failure));
        }
    }

    private boolean isCurrent(int generation) {
        return !isFinishing() && !isDestroyed() && generation == loadGeneration;
    }

    private void showLoading() {
        loading.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
    }

    private void showContent() {
        loading.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        errorMessage.setText(message);
    }

    private String weekLabel(CurriculumWeek week) {
        return String.format(Locale.US, "W%02d · %s", week.getWeekNumber(), week.getTitle());
    }
}
