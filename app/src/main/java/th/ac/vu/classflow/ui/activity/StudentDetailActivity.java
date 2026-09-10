package th.ac.vu.classflow.ui.activity;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.DataNotFoundException;
import th.ac.vu.classflow.data.repository.DataValidationException;
import th.ac.vu.classflow.data.repository.EnrollmentRepository;
import th.ac.vu.classflow.data.repository.ProgressMetricSchema;
import th.ac.vu.classflow.data.repository.StudentProgressRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import android.view.MenuItem;
import th.ac.vu.classflow.data.assessment.AssessmentCalculator;
import th.ac.vu.classflow.data.assessment.AssessmentRubricCatalog;
import th.ac.vu.classflow.data.model.AssessmentCalculation;
import th.ac.vu.classflow.data.model.AssessmentData;
import th.ac.vu.classflow.data.model.DomainResult;
import th.ac.vu.classflow.ui.adapter.StudentLearningCardAdapter;
import th.ac.vu.classflow.ui.adapter.StudentProgressWeekAdapter;
import th.ac.vu.classflow.ui.dialog.EnrollmentDialogFragment;
import th.ac.vu.classflow.ui.model.EnrollmentCardItem;
import th.ac.vu.classflow.ui.model.LearningPathSummary;
import th.ac.vu.classflow.ui.model.StudentProgressWeekItem;
import th.ac.vu.classflow.ui.util.SystemBarInsets;
import th.ac.vu.classflow.ui.view.RadarChartView;

public final class StudentDetailActivity extends ProtectedActivity {
    public static final String EXTRA_STUDENT_ID = "th.ac.vu.classflow.extra.STUDENT_ID";
    public static final String EXTRA_CLASS_ID = "th.ac.vu.classflow.extra.CLASS_ID";
    public static final String EXTRA_WEEK_ID = "th.ac.vu.classflow.extra.WEEK_ID";
    private final StudentRepository studentRepository = new StudentRepository();
    private final EnrollmentRepository enrollmentRepository = new EnrollmentRepository();
    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final StudentProgressRepository progressRepository = new StudentProgressRepository();
    private final List<EnrollmentCardItem> enrollmentItems = new ArrayList<>();
    private final List<CurriculumWeek> weeks = new ArrayList<>();
    private final Map<String, StudentProgress> progressByWeek = new LinkedHashMap<>();
    private final Map<String, MetricControl> metricControls = new LinkedHashMap<>();
    private View content, loading, error, progressArea, readOnly, notRecorded, assessmentCard, assessmentDetailsContainer;
    private View learningPathCard, noEnrollmentsCard, selectedContextContainer;
    private TextView errorMessage, name, nickname, studentIdView, editorWeek;
    private TextView assessmentUnsupportedTrack, assessmentEmptyMessage, assessmentOverallScore;
    private TextView assessmentCoverageText, assessmentStrengthText, assessmentNextStepText;
    private TextView learningPathText, currentEmpty, historyEmpty, selectedContextHeader, noEnrollmentsHint;
    private Chip identityStatus, assessmentOverallLevelChip;
    private AutoCompleteTextView overallInput;
    private TextInputEditText blockerInput, noteInput;
    private TextInputLayout blockerLayout, noteLayout;
    private LinearLayout metricContainer, assessmentDomainsContainer;
    private RadarChartView assessmentRadarChart;
    private MaterialButton saveButton, assessmentActionButton, manageEnrollmentButton, enrollInClassButton;
    private StudentLearningCardAdapter currentAdapter;
    private StudentLearningCardAdapter historyAdapter;
    private StudentProgressWeekAdapter journeyAdapter;
    private Student student;
    private EnrollmentCardItem selectedContext;
    private CurriculumWeek selectedWeek;
    private String selectedOverall = StudentProgress.STATUS_NOT_STARTED;
    private int generation;
    private boolean writeInFlight;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_student_detail);
        SystemBarInsets.applyToRoot(findViewById(R.id.student_detail_root));
        MaterialToolbar toolbar = findViewById(R.id.student_detail_toolbar);
        configureToolbar(toolbar);
        bind();
        currentAdapter = new StudentLearningCardAdapter(this::selectContext);
        RecyclerView currentRecycler = findViewById(R.id.student_current_learning_recycler);
        currentRecycler.setLayoutManager(new LinearLayoutManager(this));
        currentRecycler.setAdapter(currentAdapter);
        historyAdapter = new StudentLearningCardAdapter(this::selectContext);
        RecyclerView historyRecycler = findViewById(R.id.student_learning_history_recycler);
        historyRecycler.setLayoutManager(new LinearLayoutManager(this));
        historyRecycler.setAdapter(historyAdapter);
        journeyAdapter = new StudentProgressWeekAdapter(this::selectWeek);
        RecyclerView journey = findViewById(R.id.student_progress_journey);
        journey.setLayoutManager(new LinearLayoutManager(this));
        journey.setAdapter(journeyAdapter);
        findViewById(R.id.student_detail_retry).setOnClickListener(view -> load());
        manageEnrollmentButton.setOnClickListener(v -> openManageEnrollment());
        enrollInClassButton.setOnClickListener(v -> openManageEnrollment());
        saveButton.setOnClickListener(view -> saveProgress());
        assessmentActionButton.setOnClickListener(view -> {
            if (selectedContext == null || student == null) return;
            startActivity(StudentAssessmentActivity.createIntent(this, student.getStudentId(), selectedContext.getEnrollment().getClassId()));
        });
        getSupportFragmentManager().setFragmentResultListener(
                EnrollmentDialogFragment.RESULT_ENROLLMENT_CHANGED, this,
                (key, bundle) -> load());
        configureOverallInput();
        load();
    }

    private void configureToolbar(MaterialToolbar toolbar) {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.back);
        toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());

        toolbar.getMenu().clear();
        toolbar.getMenu().add(0, 1, 0, R.string.manage_enrollment).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openManageEnrollment();
                return true;
            }
            return false;
        });
    }

    private void openManageEnrollment() {
        if (student == null) return;
        EnrollmentDialogFragment.newInstance(student.getStudentId())
                .show(getSupportFragmentManager(), "manage_enrollment");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedContext != null && student != null) {
            String classId = selectedContext.getEnrollment().getClassId();
            enrollmentRepository.loadEnrollment(classId, student.getStudentId())
                    .addOnSuccessListener(updated -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (selectedContext != null && selectedContext.getEnrollment().getClassId().equals(updated.getClassId())) {
                            selectedContext = new EnrollmentCardItem(updated, selectedContext.getClassOffering(), selectedContext.getTrack());
                            renderAssessmentProfile(selectedContext);
                            for (int i = 0; i < enrollmentItems.size(); i++) {
                                if (enrollmentItems.get(i).getEnrollment().getClassId().equals(updated.getClassId())) {
                                    enrollmentItems.set(i, selectedContext);
                                    break;
                                }
                            }
                            updateAdapters();
                            renderEditor();
                        }
                    });
        }
    }

    @Override protected void onDestroy() { generation++; super.onDestroy(); }

    private void bind() {
        content = findViewById(R.id.student_detail_content);
        loading = findViewById(R.id.student_detail_loading);
        error = findViewById(R.id.student_detail_error);
        errorMessage = findViewById(R.id.student_detail_error_message);

        name = findViewById(R.id.student_detail_name);
        nickname = findViewById(R.id.student_detail_nickname);
        studentIdView = findViewById(R.id.student_detail_id);
        identityStatus = findViewById(R.id.student_detail_identity_status);
        manageEnrollmentButton = findViewById(R.id.student_detail_manage_enrollment);

        learningPathCard = findViewById(R.id.student_learning_path_card);
        learningPathText = findViewById(R.id.student_learning_path_text);

        currentEmpty = findViewById(R.id.student_current_learning_empty);
        historyEmpty = findViewById(R.id.student_learning_history_empty);

        noEnrollmentsCard = findViewById(R.id.student_no_enrollments_card);
        noEnrollmentsHint = findViewById(R.id.tv_no_enrollments_hint);
        enrollInClassButton = findViewById(R.id.btn_enroll_in_class);

        selectedContextContainer = findViewById(R.id.student_selected_context_container);
        selectedContextHeader = findViewById(R.id.student_selected_context_header);

        progressArea = findViewById(R.id.student_progress_area);
        editorWeek = findViewById(R.id.progress_editor_week);
        notRecorded = findViewById(R.id.progress_not_recorded);
        readOnly = findViewById(R.id.progress_read_only);
        metricContainer = findViewById(R.id.progress_metric_container);
        overallInput = findViewById(R.id.progress_overall_status);
        blockerInput = findViewById(R.id.progress_blocker);
        noteInput = findViewById(R.id.progress_teacher_note);
        blockerLayout = findViewById(R.id.progress_blocker_layout);
        noteLayout = findViewById(R.id.progress_teacher_note_layout);
        saveButton = findViewById(R.id.progress_save);

        assessmentCard = findViewById(R.id.student_assessment_card);
        assessmentDetailsContainer = findViewById(R.id.assessment_details_container);
        assessmentUnsupportedTrack = findViewById(R.id.assessment_unsupported_track);
        assessmentEmptyMessage = findViewById(R.id.assessment_empty_message);
        assessmentOverallScore = findViewById(R.id.assessment_overall_score);
        assessmentOverallLevelChip = findViewById(R.id.assessment_overall_level_chip);
        assessmentCoverageText = findViewById(R.id.assessment_coverage_text);
        assessmentRadarChart = findViewById(R.id.assessment_radar_chart);
        assessmentDomainsContainer = findViewById(R.id.assessment_domains_container);
        assessmentStrengthText = findViewById(R.id.assessment_strength_text);
        assessmentNextStepText = findViewById(R.id.assessment_next_step_text);
        assessmentActionButton = findViewById(R.id.assessment_action_button);
    }

    private void load() {
        int request = ++generation;
        showLoading();
        String studentId = getIntent().getStringExtra(EXTRA_STUDENT_ID);
        if (blank(studentId)) { showError(new DataNotFoundException("Student ID was not supplied.")); return; }
        Task<Student> studentTask = studentRepository.loadStudent(studentId.trim());
        Task<List<Enrollment>> enrollmentTask = enrollmentRepository.loadForStudent(studentId.trim());
        Tasks.whenAllComplete(studentTask, enrollmentTask).addOnCompleteListener(done -> {
            if (!current(request)) return;
            if (!studentTask.isSuccessful()) { showError(studentTask.getException()); return; }
            if (!enrollmentTask.isSuccessful()) { showError(enrollmentTask.getException()); return; }
            student = studentTask.getResult();
            resolveEnrollments(enrollmentTask.getResult(), request);
        });
    }

    private void resolveEnrollments(List<Enrollment> enrollments, int request) {
        if (enrollments.isEmpty()) {
            enrollmentItems.clear();
            renderIdentity();
            configureInitialContext();
            return;
        }

        List<Task<EnrollmentCardItem>> tasks = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            tasks.add(classRepository.loadClass(enrollment.getClassId()).continueWithTask(classTask -> {
                ClassOffering selectedClass = classTask.getResult();
                if (!selectedClass.getTrackId().equals(enrollment.getTrackId())) throw new DataValidationException("Enrollment Track does not match its Class.");
                return trackRepository.loadTrack(enrollment.getTrackId()).continueWith(trackTask -> new EnrollmentCardItem(enrollment, selectedClass, trackTask.getResult()));
            }));
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            if (!current(request)) return;
            enrollmentItems.clear();
            for (Task<EnrollmentCardItem> task : tasks) {
                if (!task.isSuccessful()) { showError(task.getException()); return; }
                enrollmentItems.add(task.getResult());
            }
            enrollmentItems.sort(Comparator.comparing(item -> item.getClassOffering().getName(), String.CASE_INSENSITIVE_ORDER));
            renderIdentity();
            configureInitialContext();
        });
    }

    private void renderIdentity() {
        name.setText(student.getName());
        nickname.setText(student.getNickname());
        nickname.setVisibility(blank(student.getNickname()) ? View.GONE : View.VISIBLE);
        studentIdView.setText(student.getStudentId());
        identityStatus.setText(student.isActive() ? R.string.identity_active : R.string.identity_archived);
        identityStatus.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this,
                student.isActive() ? R.color.status_active_container : R.color.status_archived_container)));

        LearningPathSummary path = LearningPathSummary.from(this, enrollmentItems);
        if (path.isVisible()) {
            learningPathCard.setVisibility(View.VISIBLE);
            learningPathText.setText(path.getSummaryText());
        } else {
            learningPathCard.setVisibility(View.GONE);
        }

        updateAdapters();
    }

    private void updateAdapters() {
        List<EnrollmentCardItem> activeList = new ArrayList<>();
        List<EnrollmentCardItem> historyList = new ArrayList<>();
        for (EnrollmentCardItem item : enrollmentItems) {
            if (Enrollment.STATUS_ACTIVE.equals(item.getEnrollment().getStatus())) {
                activeList.add(item);
            } else {
                historyList.add(item);
            }
        }

        String selectedId = selectedContext != null ? selectedContext.getEnrollment().getEnrollmentId() : null;
        currentAdapter.submitList(activeList, selectedId);
        historyAdapter.submitList(historyList, selectedId);

        if (enrollmentItems.isEmpty()) {
            currentEmpty.setVisibility(View.GONE);
            historyEmpty.setVisibility(View.GONE);
            noEnrollmentsCard.setVisibility(View.VISIBLE);
            boolean isArchived = student != null && !student.isActive();
            if (isArchived) {
                noEnrollmentsHint.setVisibility(View.VISIBLE);
                enrollInClassButton.setVisibility(View.GONE);
            } else {
                noEnrollmentsHint.setVisibility(View.GONE);
                enrollInClassButton.setVisibility(View.VISIBLE);
            }
        } else {
            noEnrollmentsCard.setVisibility(View.GONE);
            currentEmpty.setVisibility(activeList.isEmpty() ? View.VISIBLE : View.GONE);
            historyEmpty.setVisibility(historyList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void configureInitialContext() {
        String suppliedClassId = getIntent().getStringExtra(EXTRA_CLASS_ID);
        if (!blank(suppliedClassId)) {
            for (EnrollmentCardItem item : enrollmentItems) {
                if (suppliedClassId.trim().equals(item.getEnrollment().getClassId())) {
                    showContent();
                    selectContext(item);
                    return;
                }
            }
            showError(new DataNotFoundException("The requested Class does not belong to this Student's Enrollment history."));
            return;
        }

        List<EnrollmentCardItem> active = new ArrayList<>();
        for (EnrollmentCardItem item : enrollmentItems) {
            if (Enrollment.STATUS_ACTIVE.equals(item.getEnrollment().getStatus())) active.add(item);
        }

        showContent();
        if (active.size() == 1) {
            selectContext(active.get(0));
        } else {
            clearSelectedContext();
        }
    }

    private void clearSelectedContext() {
        selectedContext = null;
        currentAdapter.setSelectedEnrollmentId(null);
        historyAdapter.setSelectedEnrollmentId(null);

        if (enrollmentItems.isEmpty()) {
            selectedContextContainer.setVisibility(View.GONE);
        } else {
            selectedContextContainer.setVisibility(View.VISIBLE);
            selectedContextHeader.setText(R.string.choose_record_prompt);
        }
        assessmentCard.setVisibility(View.GONE);
        progressArea.setVisibility(View.GONE);
    }

    private void selectContext(EnrollmentCardItem item) {
        if (writeInFlight) return;
        selectedContext = item;
        currentAdapter.setSelectedEnrollmentId(item.getEnrollment().getEnrollmentId());
        historyAdapter.setSelectedEnrollmentId(item.getEnrollment().getEnrollmentId());

        selectedContextContainer.setVisibility(View.VISIBLE);
        selectedContextHeader.setText(getString(R.string.selected_context_header_format,
                item.getClassOffering().getName(), item.getTrack().getCode(), item.getEnrollment().getStatus()));

        int request = ++generation;
        progressArea.setVisibility(View.GONE);
        renderAssessmentProfile(item);
        Task<List<CurriculumWeek>> weeksTask = trackRepository.loadWeeks(item.getEnrollment().getTrackId());
        Task<List<StudentProgress>> progressTask = progressRepository.loadForStudentClass(student.getStudentId(), item.getEnrollment().getClassId());
        Tasks.whenAllComplete(weeksTask, progressTask).addOnCompleteListener(done -> {
            if (!current(request) || selectedContext != item) return;
            if (!weeksTask.isSuccessful()) { showError(weeksTask.getException()); return; }
            if (!progressTask.isSuccessful()) { showError(progressTask.getException()); return; }
            weeks.clear(); weeks.addAll(weeksTask.getResult());
            if (weeks.size() != item.getTrack().getTotalWeeks()) { showError(new DataValidationException("Curriculum data is incomplete for this Track.")); return; }
            progressByWeek.clear();
            for (StudentProgress progress : progressTask.getResult()) {
                CurriculumWeek week = findWeek(progress.getWeekId());
                if (week == null || progress.getWeekNumber() != week.getWeekNumber()
                        || !item.getEnrollment().getTrackId().equals(progress.getTrackId())
                        || !new LinkedHashSet<>(progress.getMetrics().keySet()).equals(new LinkedHashSet<>(ProgressMetricSchema.resolve(progress.getTrackId(), week.getProgressType(), week.getWeekNumber())))) {
                    showError(new DataValidationException("Stored Student Progress is inconsistent with its curriculum Week.")); return;
                }
                progressByWeek.put(progress.getWeekId(), progress);
            }
            String requestedWeekId = getIntent().getStringExtra(EXTRA_WEEK_ID);
            CurriculumWeek initial;
            if (!blank(requestedWeekId)) {
                initial = findWeek(requestedWeekId.trim());
                if (initial == null) { showError(new DataNotFoundException("The requested Week does not belong to this Track.")); return; }
            } else if (Enrollment.STATUS_ACTIVE.equals(item.getEnrollment().getStatus())) {
                initial = findWeek(String.format(Locale.US, "W%02d", item.getClassOffering().getCurrentWeek()));
                if (initial == null) { showError(new DataNotFoundException("The Class current Week is missing from its Track.")); return; }
            } else {
                initial = weeks.get(0);
            }
            List<StudentProgressWeekItem> journey = new ArrayList<>();
            for (CurriculumWeek week : weeks) journey.add(new StudentProgressWeekItem(week, progressByWeek.get(week.getWeekId())));
            selectedWeek = initial;
            journeyAdapter.submitList(journey, initial.getWeekId());
            progressArea.setVisibility(View.VISIBLE);
            renderEditor();
            showContent();
        });
    }

    private void selectWeek(StudentProgressWeekItem item) {
        if (writeInFlight) return;
        selectedWeek = item.getWeek();
        journeyAdapter.setSelectedWeek(selectedWeek.getWeekId());
        renderEditor();
    }

    private void renderEditor() {
        if (selectedContext == null || selectedWeek == null) return;
        blockerLayout.setError(null);
        noteLayout.setError(null);
        metricContainer.removeAllViews();
        metricControls.clear();
        StudentProgress stored = progressByWeek.get(selectedWeek.getWeekId());
        List<String> keys = ProgressMetricSchema.resolve(selectedContext.getEnrollment().getTrackId(), selectedWeek.getProgressType(), selectedWeek.getWeekNumber());
        for (String key : keys) {
            String value = stored == null ? StudentProgress.METRIC_NOT_STARTED : stored.getMetrics().get(key);
            metricControls.put(key, addMetricControl(key, value));
        }
        selectedOverall = stored == null ? StudentProgress.STATUS_NOT_STARTED : stored.getOverallStatus();
        overallInput.setText(selectedOverall, false);
        blockerInput.setText(stored == null ? "" : stored.getBlocker());
        noteInput.setText(stored == null ? "" : stored.getTeacherNote());
        editorWeek.setText(getString(R.string.progress_editor_week_format, selectedWeek.getWeekNumber(), selectedWeek.getTitle()));
        notRecorded.setVisibility(stored == null ? View.VISIBLE : View.GONE);
        boolean editable = Enrollment.STATUS_ACTIVE.equals(selectedContext.getEnrollment().getStatus());
        readOnly.setVisibility(editable ? View.GONE : View.VISIBLE);
        setEditorEnabled(editable);
    }

    private MetricControl addMetricControl(String key, String initial) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(ProgressMetricSchema.label(key));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        layout.setLayoutParams(params);
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint(ProgressMetricSchema.label(key));
        input.setInputType(0);
        input.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ProgressMetricSchema.metricValues()));
        input.setThreshold(0);
        input.setText(initial, false);
        MetricControl control = new MetricControl(input, initial);
        input.setOnClickListener(view -> {
            input.setText("", false);
            input.showDropDown();
        });
        input.setOnDismissListener(() -> {
            if (input.getText().length() == 0) input.setText(control.value, false);
        });
        input.setOnItemClickListener((parent, view, position, id) -> control.value = ProgressMetricSchema.metricValues().get(position));
        layout.addView(input, new TextInputLayout.LayoutParams(TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        metricContainer.addView(layout);
        return control;
    }

    private void configureOverallInput() {
        overallInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ProgressMetricSchema.overallStatuses()));
        overallInput.setThreshold(0);
        overallInput.setOnClickListener(view -> {
            overallInput.setText("", false);
            overallInput.showDropDown();
        });
        overallInput.setOnDismissListener(() -> {
            if (overallInput.getText().length() == 0) overallInput.setText(selectedOverall, false);
        });
        overallInput.setOnItemClickListener((parent, view, position, id) -> selectedOverall = ProgressMetricSchema.overallStatuses().get(position));
    }

    private void saveProgress() {
        if (writeInFlight || selectedContext == null || selectedWeek == null
                || !Enrollment.STATUS_ACTIVE.equals(selectedContext.getEnrollment().getStatus())) return;
        blockerLayout.setError(null); noteLayout.setError(null);
        String blocker = text(blockerInput), note = text(noteInput);
        if (StudentProgress.STATUS_BLOCKED.equals(selectedOverall) && blocker.isEmpty()) { blockerLayout.setError(getString(R.string.blocker_required)); return; }
        if (note.length() > 500) { noteLayout.setError(getString(R.string.teacher_note_too_long)); return; }
        Map<String, String> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, MetricControl> entry : metricControls.entrySet()) metrics.put(entry.getKey(), entry.getValue().value);
        StudentProgress input = new StudentProgress(selectedContext.getEnrollment().getClassId(), selectedContext.getEnrollment().getTrackId(), student.getStudentId(), selectedWeek.getWeekId(), selectedWeek.getWeekNumber(), metrics, selectedOverall, blocker, note);
        String savingWeekId = selectedWeek.getWeekId();
        writeInFlight = true; setEditorEnabled(false);
        try {
            progressRepository.save(input).addOnSuccessListener(done -> progressRepository
                    .loadOne(input.getClassId(), input.getStudentId(), input.getWeekId())
                    .addOnSuccessListener(saved -> {
                        if (isFinishing() || isDestroyed()) return;
                        writeInFlight = false;
                        if (saved == null) { Snackbar.make(content, R.string.progress_confirmation_failed, Snackbar.LENGTH_LONG).show(); setEditorEnabled(true); return; }
                        progressByWeek.put(savingWeekId, saved);
                        journeyAdapter.updateProgress(savingWeekId, saved);
                        if (selectedWeek != null && savingWeekId.equals(selectedWeek.getWeekId())) renderEditor();
                        Snackbar.make(content, R.string.progress_saved, Snackbar.LENGTH_SHORT).show();
                    }).addOnFailureListener(this::showSaveFailure))
                    .addOnFailureListener(this::showSaveFailure);
        } catch (RuntimeException failure) { showSaveFailure(failure); }
    }

    private void showSaveFailure(Exception failure) {
        if (isFinishing() || isDestroyed()) return;
        writeInFlight = false;
        setEditorEnabled(selectedContext != null && Enrollment.STATUS_ACTIVE.equals(selectedContext.getEnrollment().getStatus()));
        Snackbar.make(content, FirestoreErrorMessages.forException(failure), Snackbar.LENGTH_LONG).show();
    }

    private void setEditorEnabled(boolean enabled) {
        for (MetricControl control : metricControls.values()) control.input.setEnabled(enabled);
        overallInput.setEnabled(enabled); blockerInput.setEnabled(enabled); noteInput.setEnabled(enabled);
        saveButton.setEnabled(enabled && !writeInFlight);
        saveButton.setVisibility(enabled || writeInFlight ? View.VISIBLE : View.GONE);
    }

    private CurriculumWeek findWeek(String id) { for (CurriculumWeek week : weeks) if (id.equals(week.getWeekId())) return week; return null; }
    private String contextLabel(EnrollmentCardItem item) { return item.getTrack().getCode() + " · " + item.getClassOffering().getName(); }
    private String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString().trim(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private boolean current(int request) { return !isFinishing() && !isDestroyed() && request == generation; }
    private void showLoading() { content.setVisibility(View.GONE); error.setVisibility(View.GONE); loading.setVisibility(View.VISIBLE); }
    private void showContent() { loading.setVisibility(View.GONE); error.setVisibility(View.GONE); content.setVisibility(View.VISIBLE); }
    private void showError(Exception failure) { loading.setVisibility(View.GONE); content.setVisibility(View.GONE); error.setVisibility(View.VISIBLE); errorMessage.setText(FirestoreErrorMessages.forException(failure)); }

    private void renderAssessmentProfile(@Nullable EnrollmentCardItem item) {
        if (item == null) {
            assessmentCard.setVisibility(View.GONE);
            return;
        }
        assessmentCard.setVisibility(View.VISIBLE);

        boolean isSupportedTrack = AssessmentRubricCatalog.TRACK_SS1.equalsIgnoreCase(item.getEnrollment().getTrackId());
        if (!isSupportedTrack) {
            assessmentUnsupportedTrack.setVisibility(View.VISIBLE);
            assessmentEmptyMessage.setVisibility(View.GONE);
            assessmentDetailsContainer.setVisibility(View.GONE);
            assessmentActionButton.setVisibility(View.GONE);
            return;
        }

        assessmentUnsupportedTrack.setVisibility(View.GONE);
        AssessmentData assessmentData = item.getEnrollment().getParsedAssessment();
        boolean isActiveEnrollment = Enrollment.STATUS_ACTIVE.equals(item.getEnrollment().getStatus());

        if (assessmentData == null) {
            assessmentDetailsContainer.setVisibility(View.GONE);
            if (isActiveEnrollment) {
                assessmentEmptyMessage.setVisibility(View.VISIBLE);
                assessmentEmptyMessage.setText(R.string.not_assessed_yet);
                assessmentActionButton.setVisibility(View.VISIBLE);
                assessmentActionButton.setText(R.string.start_assessment);
            } else {
                assessmentEmptyMessage.setVisibility(View.VISIBLE);
                assessmentEmptyMessage.setText(R.string.no_assessment_recorded);
                assessmentActionButton.setVisibility(View.GONE);
            }
            return;
        }

        assessmentEmptyMessage.setVisibility(View.GONE);
        assessmentDetailsContainer.setVisibility(View.VISIBLE);
        assessmentActionButton.setVisibility(View.VISIBLE);
        if (isActiveEnrollment) {
            assessmentActionButton.setText(R.string.view_edit_assessment);
        } else {
            assessmentActionButton.setText(R.string.view_assessment);
        }

        AssessmentCalculation calc = AssessmentCalculator.calculate(assessmentData);

        if (calc.isOverallAvailable()) {
            assessmentOverallScore.setText(String.format(Locale.US, "%.2f", calc.getOverallScore()));
            assessmentOverallLevelChip.setVisibility(View.VISIBLE);
            assessmentOverallLevelChip.setText(calc.getOverallLevel());
            int chipBgColor;
            if (AssessmentCalculation.LEVEL_STRONG.equals(calc.getOverallLevel())) {
                chipBgColor = ContextCompat.getColor(this, R.color.status_active_container);
            } else if (AssessmentCalculation.LEVEL_READY.equals(calc.getOverallLevel())) {
                chipBgColor = ContextCompat.getColor(this, R.color.status_completed_container);
            } else if (AssessmentCalculation.LEVEL_DEVELOPING.equals(calc.getOverallLevel())) {
                chipBgColor = ContextCompat.getColor(this, R.color.status_attention_container);
            } else if (AssessmentCalculation.LEVEL_NEED_SUPPORT.equals(calc.getOverallLevel())) {
                chipBgColor = ContextCompat.getColor(this, R.color.status_blocked_container);
            } else {
                chipBgColor = ContextCompat.getColor(this, R.color.status_archived_container);
            }
            assessmentOverallLevelChip.setChipBackgroundColor(ColorStateList.valueOf(chipBgColor));
        } else {
            assessmentOverallScore.setText(R.string.not_enough_evidence);
            assessmentOverallLevelChip.setVisibility(View.GONE);
        }

        assessmentCoverageText.setText(getString(R.string.assessment_coverage_format,
                calc.getObservedCriteriaCount(),
                calc.getTotalCriteriaCount(),
                calc.getCriteriaCoveragePercent(),
                calc.getAvailableDomainCount(),
                calc.getTotalDomainCount()));

        assessmentRadarChart.setData(calc);

        assessmentDomainsContainer.removeAllViews();
        for (DomainResult dr : calc.getDomainResults()) {
            TextView line = new TextView(this);
            line.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            line.setPadding(0, dp(4), 0, dp(4));
            String scoreStr = dr.isAvailable() ? String.format(Locale.US, "%.2f", dr.getScore()) : getString(R.string.overall_score_incomplete);
            String statusStr = getString(R.string.domain_coverage_format, dr.getObservedCount());
            line.setText(getString(R.string.domain_score_format, scoreStr, statusStr) + " — " + dr.getDomainTitle());
            line.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            line.setTextColor(ContextCompat.getColor(this, dr.isAvailable() ? R.color.indigo_primary_dark : R.color.slate_gray));
            assessmentDomainsContainer.addView(line);
        }

        String strength = assessmentData.getStrength();
        if (strength != null && !strength.trim().isEmpty()) {
            assessmentStrengthText.setVisibility(View.VISIBLE);
            assessmentStrengthText.setText(getString(R.string.strength_card_format, strength.trim()));
        } else {
            assessmentStrengthText.setVisibility(View.GONE);
        }

        String nextStep = assessmentData.getNextStep();
        if (nextStep != null && !nextStep.trim().isEmpty()) {
            assessmentNextStepText.setVisibility(View.VISIBLE);
            assessmentNextStepText.setText(getString(R.string.next_step_card_format, nextStep.trim()));
        } else {
            assessmentNextStepText.setVisibility(View.GONE);
        }
    }

    private static final class MetricControl {
        final AutoCompleteTextView input;
        String value;
        MetricControl(AutoCompleteTextView input, String value) { this.input = input; this.value = value; }
    }
}
