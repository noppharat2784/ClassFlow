package th.ac.vu.classflow.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.EnrollmentRepository;
import th.ac.vu.classflow.data.repository.StudentProgressRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.ui.adapter.StudentStatusAdapter;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.StudentStatusItem;
import th.ac.vu.classflow.ui.util.SystemBarInsets;

public final class WeekDetailActivity extends ProtectedActivity {
    public static final String EXTRA_CLASS_ID = "th.ac.vu.classflow.extra.CLASS_ID";
    public static final String EXTRA_WEEK_ID = "th.ac.vu.classflow.extra.WEEK_ID";
    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final EnrollmentRepository enrollmentRepository = new EnrollmentRepository();
    private final StudentRepository studentRepository = new StudentRepository();
    private final StudentProgressRepository progressRepository = new StudentProgressRepository();
    private final List<StudentStatusItem> allStudents = new ArrayList<>();
    private View content, loading, error;
    private TextView errorMessage, empty, counts;
    private StudentStatusAdapter adapter;
    private String classId, weekId;
    private String statusFilter = ProgressPresentation.FILTER_ALL;
    private int loadGeneration;
    private boolean resumedOnce;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_week_detail);
        SystemBarInsets.applyToRoot(findViewById(R.id.week_detail_root));
        MaterialToolbar toolbar = findViewById(R.id.week_detail_toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.back);
        toolbar.setNavigationOnClickListener(view -> finish());
        content = findViewById(R.id.week_detail_content);
        loading = findViewById(R.id.week_detail_loading);
        error = findViewById(R.id.week_detail_error);
        errorMessage = findViewById(R.id.week_detail_error_message);
        empty = findViewById(R.id.week_detail_students_empty);
        counts = findViewById(R.id.week_detail_counts);
        adapter = new StudentStatusAdapter(this::openStudent);
        RecyclerView list = findViewById(R.id.week_detail_student_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        bindFilters();
        findViewById(R.id.week_detail_retry).setOnClickListener(view -> loadWeek());
        classId = getIntent().getStringExtra(EXTRA_CLASS_ID);
        weekId = getIntent().getStringExtra(EXTRA_WEEK_ID);
        if (blank(classId) || blank(weekId)) { showError("Class or Week identity is missing."); return; }
        classId = classId.trim();
        weekId = weekId.trim();
        loadWeek();
    }

    @Override protected void onResume() {
        super.onResume();
        if (resumedOnce && classId != null && weekId != null && content != null) loadWeek();
        resumedOnce = true;
    }
    @Override protected void onDestroy() { loadGeneration++; super.onDestroy(); }

    private void loadWeek() {
        int request = ++loadGeneration;
        showLoading();
        classRepository.loadClass(classId).addOnSuccessListener(selectedClass -> {
            if (!isCurrent(request)) return;
            Task<th.ac.vu.classflow.data.model.CurriculumWeek> weekTask = trackRepository.loadWeek(selectedClass.getTrackId(), weekId);
            Task<List<Enrollment>> enrollmentTask = enrollmentRepository.loadActiveForClass(classId);
            Task<List<StudentProgress>> progressTask = progressRepository.loadForClassWeek(classId, weekId);
            Tasks.whenAllComplete(weekTask, enrollmentTask, progressTask).addOnCompleteListener(done -> {
                if (!isCurrent(request)) return;
                if (!weekTask.isSuccessful()) { showFailure(request, weekTask.getException()); return; }
                if (!enrollmentTask.isSuccessful()) { showFailure(request, enrollmentTask.getException()); return; }
                if (!progressTask.isSuccessful()) { showFailure(request, progressTask.getException()); return; }
                bindHeader(weekTask.getResult());
                assembleStudents(selectedClass, enrollmentTask.getResult(), progressTask.getResult(), request);
            });
        }).addOnFailureListener(failure -> showFailure(request, failure));
    }

    private void bindHeader(th.ac.vu.classflow.data.model.CurriculumWeek week) {
        ((TextView) findViewById(R.id.week_detail_title)).setText(getString(R.string.week_detail_format, week.getWeekNumber(), week.getTitle()));
        ((TextView) findViewById(R.id.week_detail_summary)).setText(week.getSummary());
        ((TextView) findViewById(R.id.week_detail_phase)).setText(getString(R.string.phase_format, week.getPhase()));
        ((TextView) findViewById(R.id.week_detail_progress_type)).setText(getString(R.string.progress_type_format, week.getProgressType()));
    }

    private void assembleStudents(ClassOffering selectedClass, List<Enrollment> enrollments,
                                  List<StudentProgress> progresses, int request) {
        Map<String, StudentProgress> byStudent = new LinkedHashMap<>();
        for (StudentProgress progress : progresses) {
            if (!weekId.equals(progress.getWeekId()) || !selectedClass.getTrackId().equals(progress.getTrackId())) {
                showError("Student Progress is inconsistent with this Class and Week."); return;
            }
            byStudent.put(progress.getStudentId(), progress);
        }
        List<Task<Student>> tasks = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            if (!selectedClass.getTrackId().equals(enrollment.getTrackId())) {
                showError("Enrollment Track does not match this Class."); return;
            }
            tasks.add(studentRepository.loadStudent(enrollment.getStudentId()));
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            if (!isCurrent(request)) return;
            allStudents.clear();
            for (int index = 0; index < tasks.size(); index++) {
                Task<Student> task = tasks.get(index);
                if (!task.isSuccessful()) { showFailure(request, task.getException()); return; }
                Enrollment enrollment = enrollments.get(index);
                allStudents.add(new StudentStatusItem(task.getResult(), enrollment, byStudent.get(enrollment.getStudentId())));
            }
            allStudents.sort(Comparator.comparing(item -> item.getStudent().getName(), String.CASE_INSENSITIVE_ORDER));
            renderCounts();
            applyFilter();
            showContent();
        });
    }

    private void renderCounts() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (StudentStatusItem item : allStudents) values.put(item.presentationStatus(), values.getOrDefault(item.presentationStatus(), 0) + 1);
        counts.setText(getString(R.string.week_status_counts,
                values.getOrDefault(StudentProgress.STATUS_ON_TRACK, 0),
                values.getOrDefault(StudentProgress.STATUS_NEEDS_ATTENTION, 0),
                values.getOrDefault(StudentProgress.STATUS_BLOCKED, 0),
                values.getOrDefault(ProgressPresentation.NOT_RECORDED, 0),
                values.getOrDefault(StudentProgress.STATUS_NOT_STARTED, 0),
                values.getOrDefault(StudentProgress.STATUS_COMPLETED, 0)));
    }

    private void bindFilters() {
        bindFilter(R.id.week_status_all, ProgressPresentation.FILTER_ALL);
        bindFilter(R.id.week_status_not_recorded, ProgressPresentation.NOT_RECORDED);
        bindFilter(R.id.week_status_not_started, StudentProgress.STATUS_NOT_STARTED);
        bindFilter(R.id.week_status_on_track, StudentProgress.STATUS_ON_TRACK);
        bindFilter(R.id.week_status_needs_attention, StudentProgress.STATUS_NEEDS_ATTENTION);
        bindFilter(R.id.week_status_blocked, StudentProgress.STATUS_BLOCKED);
        bindFilter(R.id.week_status_completed, StudentProgress.STATUS_COMPLETED);
    }
    private void bindFilter(int id, String value) { findViewById(id).setOnClickListener(view -> { statusFilter = value; applyFilter(); }); }
    private void applyFilter() {
        if (adapter == null) return;
        List<StudentStatusItem> visible = new ArrayList<>();
        for (StudentStatusItem item : allStudents) if (ProgressPresentation.FILTER_ALL.equals(statusFilter) || statusFilter.equals(item.presentationStatus())) visible.add(item);
        adapter.submitList(visible);
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(allStudents.isEmpty() ? R.string.no_enrolled_students : R.string.no_students_for_status);
    }
    private void openStudent(StudentStatusItem item) {
        Intent intent = new Intent(this, StudentDetailActivity.class);
        intent.putExtra(StudentDetailActivity.EXTRA_STUDENT_ID, item.getStudent().getStudentId());
        intent.putExtra(StudentDetailActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(StudentDetailActivity.EXTRA_WEEK_ID, weekId);
        startActivity(intent);
    }
    private void showFailure(int request, Exception failure) { if (isCurrent(request)) showError(FirestoreErrorMessages.forException(failure)); }
    private boolean isCurrent(int request) { return !isFinishing() && !isDestroyed() && request == loadGeneration; }
    private void showLoading() { loading.setVisibility(View.VISIBLE); content.setVisibility(View.GONE); error.setVisibility(View.GONE); }
    private void showContent() { loading.setVisibility(View.GONE); content.setVisibility(View.VISIBLE); error.setVisibility(View.GONE); }
    private void showError(String message) { loading.setVisibility(View.GONE); content.setVisibility(View.GONE); error.setVisibility(View.VISIBLE); errorMessage.setText(message); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
