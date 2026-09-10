package th.ac.vu.classflow.ui.activity;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.ProgressMetricSchema;
import th.ac.vu.classflow.data.repository.ProjectProgressRepository;
import th.ac.vu.classflow.data.repository.ProjectRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.ui.adapter.ProjectProgressWeekAdapter;
import th.ac.vu.classflow.ui.dialog.ProjectEditorDialogFragment;
import th.ac.vu.classflow.ui.model.ProgressPresentation;
import th.ac.vu.classflow.ui.model.ProjectProgressWeekItem;
import th.ac.vu.classflow.ui.util.SystemBarInsets;

public final class ProjectDetailActivity extends ProtectedActivity {

    public static final String EXTRA_PROJECT_ID = "th.ac.vu.classflow.extra.PROJECT_ID";

    private final ProjectRepository projectRepository = new ProjectRepository();
    private final ProjectProgressRepository progressRepository = new ProjectProgressRepository();
    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final StudentRepository studentRepository = new StudentRepository();

    private String projectId;
    private Project project;
    private ClassOffering classOffering;
    private Track track;
    private final List<CurriculumWeek> curriculumWeeks = new ArrayList<>();
    private final List<Student> memberStudents = new ArrayList<>();
    private final Map<String, ProjectProgress> progressByWeekId = new LinkedHashMap<>();

    private int selectedWeekNumber = 1;
    @Nullable private CurriculumWeek selectedCurriculumWeek;
    private final Map<String, MetricControl> metricControls = new LinkedHashMap<>();
    private String selectedOverall = Project.STATUS_NOT_STARTED;

    private MaterialToolbar toolbar;
    private View content;
    private View loading;
    private View errorContainer;
    private TextView errorMessage;

    private TextView titleText;
    private TextView teamNameText;
    private TextView contextText;
    private Chip weekChip;
    private Chip statusChip;
    private Chip archivedChip;
    private ChipGroup memberChips;

    private RecyclerView journeyRecycler;
    private ProjectProgressWeekAdapter journeyAdapter;

    private TextView editorWeekTitle;
    private TextView notRecordedNotice;
    private TextView readOnlyNotice;
    private LinearLayout metricContainer;
    private AutoCompleteTextView overallInput;
    private TextInputLayout blockerLayout;
    private TextInputEditText blockerInput;
    private TextInputLayout noteLayout;
    private TextInputEditText noteInput;
    private MaterialButton setAsCurrentWeekButton;
    private MaterialButton saveProgressButton;

    private int generation;
    private boolean writeInFlight;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_detail);
        SystemBarInsets.applyToRoot(findViewById(R.id.project_detail_root));

        projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        if (projectId == null || projectId.isEmpty()) {
            projectId = getIntent().getStringExtra("project_id");
        }

        bindViews();
        configureToolbar();
        configureInputs();
        configureJourney();

        getSupportFragmentManager().setFragmentResultListener(
                ProjectEditorDialogFragment.RESULT_PROJECT_CHANGED, this,
                (key, result) -> loadProjectDetails());

        loadProjectDetails();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.project_detail_toolbar);
        content = findViewById(R.id.project_detail_content);
        loading = findViewById(R.id.project_detail_loading);
        errorContainer = findViewById(R.id.project_detail_error);
        errorMessage = findViewById(R.id.project_detail_error_message);

        titleText = findViewById(R.id.project_detail_title);
        teamNameText = findViewById(R.id.project_detail_team_name);
        contextText = findViewById(R.id.project_detail_context);
        weekChip = findViewById(R.id.project_detail_week_chip);
        statusChip = findViewById(R.id.project_detail_status_chip);
        archivedChip = findViewById(R.id.project_detail_archived_chip);
        memberChips = findViewById(R.id.project_detail_member_chips);

        journeyRecycler = findViewById(R.id.project_progress_journey);

        editorWeekTitle = findViewById(R.id.project_progress_editor_week);
        notRecordedNotice = findViewById(R.id.project_progress_not_recorded);
        readOnlyNotice = findViewById(R.id.project_progress_read_only);
        metricContainer = findViewById(R.id.project_progress_metric_container);
        overallInput = findViewById(R.id.project_progress_overall_status);
        blockerLayout = findViewById(R.id.project_progress_blocker_layout);
        blockerInput = findViewById(R.id.project_progress_blocker);
        noteLayout = findViewById(R.id.project_progress_teacher_note_layout);
        noteInput = findViewById(R.id.project_progress_teacher_note);
        setAsCurrentWeekButton = findViewById(R.id.btn_set_as_current_week);
        saveProgressButton = findViewById(R.id.btn_save_project_progress);

        findViewById(R.id.project_detail_retry).setOnClickListener(v -> loadProjectDetails());
        setAsCurrentWeekButton.setOnClickListener(v -> changeCurrentWeek());
        saveProgressButton.setOnClickListener(v -> saveProgress());
    }

    private void configureToolbar() {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.back);
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.getMenu().clear();
        toolbar.getMenu().add(0, 1, 0, R.string.edit_project).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.getMenu().add(0, 2, 1, R.string.archive_project).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                if (project != null) {
                    ProjectEditorDialogFragment.newEdit(project.getProjectId())
                            .show(getSupportFragmentManager(), "project_edit");
                }
                return true;
            } else if (item.getItemId() == 2) {
                if (project != null && project.isActive()) {
                    confirmArchiveProject();
                }
                return true;
            }
            return false;
        });
    }

    private void configureInputs() {
        overallInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, ProgressMetricSchema.overallStatuses()));
        overallInput.setThreshold(0);
        overallInput.setOnClickListener(v -> overallInput.showDropDown());
        overallInput.setOnItemClickListener((parent, view, position, id) ->
                selectedOverall = ProgressMetricSchema.overallStatuses().get(position));
    }

    private void configureJourney() {
        journeyAdapter = new ProjectProgressWeekAdapter(item -> selectWeek(item.getWeekNumber()));
        journeyRecycler.setLayoutManager(new LinearLayoutManager(this));
        journeyRecycler.setAdapter(journeyAdapter);
    }

    private void loadProjectDetails() {
        if (projectId == null || projectId.isEmpty()) {
            showError(new IllegalArgumentException("Project ID is missing."));
            return;
        }

        int request = ++generation;
        showLoading();

        projectRepository.loadProject(projectId).addOnSuccessListener(loadedProject -> {
            if (!current(request)) return;
            this.project = loadedProject;

            Task<ClassOffering> classTask = classRepository.loadClass(project.getClassId());
            Task<Track> trackTask = trackRepository.loadTrack(project.getTrackId());
            Task<List<CurriculumWeek>> weeksTask = trackRepository.loadWeeks(project.getTrackId());
            Task<List<ProjectProgress>> progressTask = progressRepository.loadForProject(project.getProjectId());

            List<Task<Student>> memberTasks = new ArrayList<>();
            for (String mid : project.getMemberIds()) {
                memberTasks.add(studentRepository.loadStudent(mid));
            }

            Tasks.whenAllComplete(classTask, trackTask, weeksTask, progressTask, Tasks.whenAllComplete(memberTasks))
                    .addOnCompleteListener(ignored -> {
                        if (!current(request)) return;

                        if (!classTask.isSuccessful() || classTask.getResult() == null) {
                            showError(classTask.getException() != null ? classTask.getException() : new IllegalStateException("Class not found."));
                            return;
                        }
                        if (!trackTask.isSuccessful() || trackTask.getResult() == null) {
                            showError(trackTask.getException() != null ? trackTask.getException() : new IllegalStateException("Track not found."));
                            return;
                        }
                        if (!weeksTask.isSuccessful() || weeksTask.getResult() == null) {
                            showError(weeksTask.getException() != null ? weeksTask.getException() : new IllegalStateException("Curriculum weeks not found."));
                            return;
                        }

                        classOffering = classTask.getResult();
                        track = trackTask.getResult();

                        curriculumWeeks.clear();
                        curriculumWeeks.addAll(weeksTask.getResult());
                        curriculumWeeks.sort(Comparator.comparingInt(CurriculumWeek::getWeekNumber));

                        memberStudents.clear();
                        for (Task<Student> st : memberTasks) {
                            if (st.isSuccessful() && st.getResult() != null) {
                                memberStudents.add(st.getResult());
                            }
                        }

                        progressByWeekId.clear();
                        if (progressTask.isSuccessful() && progressTask.getResult() != null) {
                            for (ProjectProgress pp : progressTask.getResult()) {
                                progressByWeekId.put(pp.getWeekId(), pp);
                            }
                        }

                        renderHeader();
                        selectedWeekNumber = project.getCurrentWeek();
                        renderJourney();
                        selectWeek(selectedWeekNumber);
                        showContent();
                    });
        }).addOnFailureListener(e -> {
            if (!current(request)) return;
            showError(e);
        });
    }

    private void renderHeader() {
        titleText.setText(project.getTitle());
        if (project.getTeamName() != null && !project.getTeamName().trim().isEmpty()) {
            teamNameText.setText(project.getTeamName().trim());
            teamNameText.setVisibility(View.VISIBLE);
        } else {
            teamNameText.setVisibility(View.GONE);
        }

        contextText.setText(String.format(Locale.US, "%s · %s", track.getCode(), classOffering.getName()));
        weekChip.setText(String.format(Locale.US, "Week %d", project.getCurrentWeek()));

        statusChip.setText(ProgressPresentation.label(project.getOverallStatus()));
        bindStatusChipColor(statusChip, project.getOverallStatus());

        if (!project.isActive()) {
            archivedChip.setVisibility(View.VISIBLE);
        } else {
            archivedChip.setVisibility(View.GONE);
        }

        memberChips.removeAllViews();
        for (Student s : memberStudents) {
            Chip chip = new Chip(this);
            String displayName = (s.getNickname() != null && !s.getNickname().trim().isEmpty())
                    ? s.getNickname().trim() : s.getName();
            if (!s.isActive()) {
                displayName += " " + getString(R.string.member_archived_label);
            }
            chip.setText(displayName);
            chip.setClickable(false);
            memberChips.addView(chip);
        }

        MenuItem archiveItem = toolbar.getMenu().findItem(2);
        if (archiveItem != null) {
            archiveItem.setVisible(project.isActive());
        }
    }

    private void renderJourney() {
        List<ProjectProgressWeekItem> items = new ArrayList<>();
        for (CurriculumWeek cw : curriculumWeeks) {
            ProjectProgress pp = progressByWeekId.get(cw.getWeekId());
            boolean isCurrent = cw.getWeekNumber() == project.getCurrentWeek();
            items.add(new ProjectProgressWeekItem(
                    cw.getWeekNumber(),
                    cw.getWeekId(),
                    cw.getTitle(),
                    cw.getProgressType(),
                    pp,
                    isCurrent
            ));
        }
        journeyAdapter.submitList(items, selectedWeekNumber);
    }

    private void selectWeek(int weekNumber) {
        selectedWeekNumber = weekNumber;
        journeyAdapter.setSelectedWeekNumber(weekNumber);

        selectedCurriculumWeek = null;
        for (CurriculumWeek cw : curriculumWeeks) {
            if (cw.getWeekNumber() == weekNumber) {
                selectedCurriculumWeek = cw;
                break;
            }
        }

        renderEditor();
    }

    private void renderEditor() {
        if (selectedCurriculumWeek == null) return;

        editorWeekTitle.setText(String.format(Locale.US, "W%02d · %s Progress",
                selectedCurriculumWeek.getWeekNumber(), selectedCurriculumWeek.getTitle()));

        String weekId = selectedCurriculumWeek.getWeekId();
        ProjectProgress existing = progressByWeekId.get(weekId);

        boolean isArchived = !project.isActive();

        if (isArchived) {
            readOnlyNotice.setVisibility(View.VISIBLE);
            notRecordedNotice.setVisibility(View.GONE);
        } else {
            readOnlyNotice.setVisibility(View.GONE);
            if (existing == null) {
                notRecordedNotice.setVisibility(View.VISIBLE);
            } else {
                notRecordedNotice.setVisibility(View.GONE);
            }
        }

        List<String> metricKeys = ProgressMetricSchema.resolve(
                project.getTrackId(),
                selectedCurriculumWeek.getProgressType(),
                selectedCurriculumWeek.getWeekNumber());

        metricContainer.removeAllViews();
        metricControls.clear();

        Map<String, String> existingMetrics = existing != null ? existing.getMetrics() : Collections.emptyMap();
        for (String key : metricKeys) {
            String initial = existingMetrics.containsKey(key) ? existingMetrics.get(key) : ProjectProgress.METRIC_NOT_STARTED;
            MetricControl control = addMetricControl(key, initial);
            control.input.setEnabled(!isArchived && !writeInFlight);
            metricControls.put(key, control);
        }

        selectedOverall = existing != null ? existing.getOverallStatus() : Project.STATUS_NOT_STARTED;
        overallInput.setText(selectedOverall, false);
        overallInput.setEnabled(!isArchived && !writeInFlight);

        blockerInput.setText(existing != null && existing.getBlocker() != null ? existing.getBlocker() : "");
        blockerInput.setEnabled(!isArchived && !writeInFlight);

        noteInput.setText(existing != null && existing.getTeacherNote() != null ? existing.getTeacherNote() : "");
        noteInput.setEnabled(!isArchived && !writeInFlight);

        if (!isArchived && selectedCurriculumWeek.getWeekNumber() != project.getCurrentWeek()) {
            setAsCurrentWeekButton.setVisibility(View.VISIBLE);
            setAsCurrentWeekButton.setEnabled(!writeInFlight);
        } else {
            setAsCurrentWeekButton.setVisibility(View.GONE);
        }

        if (!isArchived) {
            saveProgressButton.setVisibility(View.VISIBLE);
            saveProgressButton.setEnabled(!writeInFlight);
        } else {
            saveProgressButton.setVisibility(View.GONE);
        }
    }

    private MetricControl addMetricControl(String key, String initial) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(ProgressMetricSchema.label(key));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        layout.setLayoutParams(params);

        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint(ProgressMetricSchema.label(key));
        input.setInputType(0);
        input.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ProgressMetricSchema.metricValues()));
        input.setThreshold(0);
        input.setText(initial, false);

        MetricControl control = new MetricControl(input, initial);
        input.setOnClickListener(v -> input.showDropDown());
        input.setOnItemClickListener((parent, view, position, id) ->
                control.value = ProgressMetricSchema.metricValues().get(position));

        layout.addView(input, new TextInputLayout.LayoutParams(
                TextInputLayout.LayoutParams.MATCH_PARENT, TextInputLayout.LayoutParams.WRAP_CONTENT));
        metricContainer.addView(layout);
        return control;
    }

    private void changeCurrentWeek() {
        if (writeInFlight || project == null || selectedCurriculumWeek == null || !project.isActive()) return;

        int targetWeek = selectedCurriculumWeek.getWeekNumber();
        writeInFlight = true;
        setEditorEnabled(false);

        projectRepository.updateCurrentWeek(project.getProjectId(), targetWeek)
                .addOnSuccessListener(unused -> {
                    if (isFinishing() || isDestroyed()) return;
                    writeInFlight = false;

                    project.setCurrentWeek(targetWeek);
                    ProjectProgress targetProgress = progressByWeekId.get(selectedCurriculumWeek.getWeekId());
                    if (targetProgress != null) {
                        project.setOverallStatus(targetProgress.getOverallStatus());
                    } else {
                        project.setOverallStatus(Project.STATUS_NOT_STARTED);
                    }

                    renderHeader();
                    renderJourney();
                    renderEditor();
                    Snackbar.make(content, R.string.project_current_week_updated, Snackbar.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    writeInFlight = false;
                    setEditorEnabled(true);
                    Snackbar.make(content, FirestoreErrorMessages.forException(e), Snackbar.LENGTH_LONG).show();
                });
    }

    private void saveProgress() {
        if (writeInFlight || project == null || selectedCurriculumWeek == null || !project.isActive()) return;

        blockerLayout.setError(null);
        noteLayout.setError(null);

        String rawOverall = overallInput.getText() != null ? overallInput.getText().toString().trim() : "";
        if (Project.isValidOverallStatus(rawOverall)) {
            selectedOverall = rawOverall;
        }

        String blocker = blockerInput.getText() != null ? blockerInput.getText().toString().trim() : "";
        String note = noteInput.getText() != null ? noteInput.getText().toString().trim() : "";

        if (Project.STATUS_BLOCKED.equals(selectedOverall) && blocker.isEmpty()) {
            blockerLayout.setError(getString(R.string.blocker_required));
            return;
        }
        if (note.length() > 500) {
            noteLayout.setError(getString(R.string.teacher_note_too_long));
            return;
        }

        Map<String, String> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, MetricControl> entry : metricControls.entrySet()) {
            String rawVal = entry.getValue().input.getText() != null ? entry.getValue().input.getText().toString().trim() : "";
            if (ProjectProgress.isValidMetricValue(rawVal)) {
                entry.getValue().value = rawVal;
            }
            metrics.put(entry.getKey(), entry.getValue().value);
        }

        ProjectProgress input = new ProjectProgress(
                project.getProjectId(),
                project.getClassId(),
                project.getTrackId(),
                selectedCurriculumWeek.getWeekId(),
                selectedCurriculumWeek.getWeekNumber(),
                metrics,
                selectedOverall,
                blocker,
                note
        );

        String savingWeekId = selectedCurriculumWeek.getWeekId();
        int savingWeekNumber = selectedCurriculumWeek.getWeekNumber();
        writeInFlight = true;
        setEditorEnabled(false);

        progressRepository.save(input).addOnSuccessListener(unused -> {
            progressRepository.loadOne(project.getProjectId(), savingWeekId)
                    .addOnSuccessListener(saved -> {
                        if (isFinishing() || isDestroyed()) return;
                        writeInFlight = false;

                        if (saved != null) {
                            progressByWeekId.put(savingWeekId, saved);
                        }

                        if (savingWeekNumber == project.getCurrentWeek()) {
                            project.setOverallStatus(input.getOverallStatus());
                            renderHeader();
                        }

                        renderJourney();
                        renderEditor();
                        Snackbar.make(content, R.string.project_progress_saved, Snackbar.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        if (isFinishing() || isDestroyed()) return;
                        writeInFlight = false;
                        setEditorEnabled(true);
                        Snackbar.make(content, FirestoreErrorMessages.forException(e), Snackbar.LENGTH_LONG).show();
                    });
        }).addOnFailureListener(e -> {
            if (isFinishing() || isDestroyed()) return;
            writeInFlight = false;
            setEditorEnabled(true);
            Snackbar.make(content, FirestoreErrorMessages.forException(e), Snackbar.LENGTH_LONG).show();
        });
    }

    private void confirmArchiveProject() {
        if (project == null || !project.isActive() || writeInFlight) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.archive_project_title)
                .setMessage(R.string.archive_project_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.archive_project, (d, w) -> {
                    showLoading();
                    projectRepository.archiveProject(project.getProjectId())
                            .addOnSuccessListener(unused -> {
                                if (isFinishing() || isDestroyed()) return;
                                loadProjectDetails();
                                Snackbar.make(content, R.string.project_archived, Snackbar.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                if (isFinishing() || isDestroyed()) return;
                                showError(e);
                            });
                })
                .show();
    }

    private void setEditorEnabled(boolean enabled) {
        for (MetricControl control : metricControls.values()) {
            control.input.setEnabled(enabled);
        }
        overallInput.setEnabled(enabled);
        blockerInput.setEnabled(enabled);
        noteInput.setEnabled(enabled);
        setAsCurrentWeekButton.setEnabled(enabled);
        saveProgressButton.setEnabled(enabled);
    }

    private static void bindStatusChipColor(Chip chip, String status) {
        android.content.Context context = chip.getContext();
        int containerColorRes;
        int textColorRes;

        if (Project.STATUS_ON_TRACK.equals(status)) {
            containerColorRes = R.color.status_active_container;
            textColorRes = R.color.status_on_track;
        } else if (Project.STATUS_NEEDS_ATTENTION.equals(status)) {
            containerColorRes = R.color.status_attention_container;
            textColorRes = R.color.status_needs_attention;
        } else if (Project.STATUS_BLOCKED.equals(status)) {
            containerColorRes = R.color.status_blocked_container;
            textColorRes = R.color.status_blocked;
        } else if (Project.STATUS_COMPLETED.equals(status)) {
            containerColorRes = R.color.status_completed_container;
            textColorRes = R.color.status_completed;
        } else {
            containerColorRes = R.color.status_planned_container;
            textColorRes = R.color.status_not_started;
        }

        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, containerColorRes)));
        chip.setTextColor(ContextCompat.getColor(context, textColorRes));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean current(int request) {
        return !isFinishing() && !isDestroyed() && request == generation;
    }

    private void showLoading() {
        content.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
    }

    private void showContent() {
        loading.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    private void showError(Exception e) {
        loading.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
        errorMessage.setText(FirestoreErrorMessages.forException(e));
    }

    private static final class MetricControl {
        final AutoCompleteTextView input;
        String value;

        MetricControl(AutoCompleteTextView input, String value) {
            this.input = input;
            this.value = value;
        }
    }
}
