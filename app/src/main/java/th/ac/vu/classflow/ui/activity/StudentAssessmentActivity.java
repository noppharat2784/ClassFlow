package th.ac.vu.classflow.ui.activity;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.assessment.AssessmentCalculator;
import th.ac.vu.classflow.data.assessment.AssessmentRubricCatalog;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.AssessmentCalculation;
import th.ac.vu.classflow.data.model.AssessmentData;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.DomainResult;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.AssessmentRepository;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.DataNotFoundException;
import th.ac.vu.classflow.data.repository.DataValidationException;
import th.ac.vu.classflow.data.repository.EnrollmentRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.ui.util.SystemBarInsets;
import th.ac.vu.classflow.ui.view.RadarChartView;

public final class StudentAssessmentActivity extends ProtectedActivity {

    public static final String EXTRA_STUDENT_ID = "th.ac.vu.classflow.extra.STUDENT_ID";
    public static final String EXTRA_CLASS_ID = "th.ac.vu.classflow.extra.CLASS_ID";

    public static android.content.Intent createIntent(android.content.Context context, String studentId, String classId) {
        android.content.Intent intent = new android.content.Intent(context, StudentAssessmentActivity.class);
        intent.putExtra(EXTRA_STUDENT_ID, studentId);
        intent.putExtra(EXTRA_CLASS_ID, classId);
        return intent;
    }

    private final StudentRepository studentRepository = new StudentRepository();
    private final ClassRepository classRepository = new ClassRepository();
    private final EnrollmentRepository enrollmentRepository = new EnrollmentRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final AssessmentRepository assessmentRepository = new AssessmentRepository();

    private final Map<String, Object> currentCriteria = new LinkedHashMap<>();
    private final Map<String, AutoCompleteTextView> criterionInputs = new LinkedHashMap<>();
    private final Map<Integer, TextView> domainScoreViews = new LinkedHashMap<>();

    private View root;
    private View content;
    private View loading;
    private View error;
    private TextView errorMessage;
    private View unsupportedTrackView;
    private TextView readOnlyBanner;

    private TextView studentName;
    private TextView studentNickname;
    private TextView classContext;
    private Chip statusChip;
    private TextView rubricLabel;

    private TextView overallScoreText;
    private Chip levelChip;
    private RadarChartView radarChart;
    private LinearLayout domainScoresSummary;
    private LinearLayout domainsContainer;

    private TextInputLayout strengthLayout;
    private TextInputEditText strengthInput;
    private TextInputLayout nextStepLayout;
    private TextInputEditText nextStepInput;
    private MaterialButton saveButton;

    private String studentId;
    private String classId;
    private Student student;
    private ClassOffering classOffering;
    private Enrollment enrollment;
    private Track track;

    private int loadGeneration;
    private boolean writeInFlight;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_assessment);

        root = findViewById(R.id.assessment_root);
        SystemBarInsets.applyToRoot(root);

        MaterialToolbar toolbar = findViewById(R.id.assessment_toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.back);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        configureActions();

        studentId = getIntent().getStringExtra(EXTRA_STUDENT_ID);
        classId = getIntent().getStringExtra(EXTRA_CLASS_ID);

        if (blank(studentId) || blank(classId)) {
            showError(new DataNotFoundException("Student ID and Class ID are required."));
            return;
        }

        studentId = studentId.trim();
        classId = classId.trim();

        loadAssessmentContext();
    }

    private void bindViews() {
        content = findViewById(R.id.assessment_content);
        loading = findViewById(R.id.assessment_loading);
        error = findViewById(R.id.assessment_error);
        errorMessage = findViewById(R.id.assessment_error_message);
        unsupportedTrackView = findViewById(R.id.assessment_unsupported);
        readOnlyBanner = findViewById(R.id.assessment_read_only_banner);

        studentName = findViewById(R.id.assessment_student_name);
        studentNickname = findViewById(R.id.assessment_student_nickname);
        classContext = findViewById(R.id.assessment_class_context);
        statusChip = findViewById(R.id.assessment_status_chip);
        rubricLabel = findViewById(R.id.assessment_rubric_label);

        overallScoreText = findViewById(R.id.assessment_overall_score);
        levelChip = findViewById(R.id.assessment_level_chip);
        radarChart = findViewById(R.id.assessment_radar_chart);
        domainScoresSummary = findViewById(R.id.assessment_domain_scores_summary);
        domainsContainer = findViewById(R.id.assessment_domains_container);

        strengthLayout = findViewById(R.id.assessment_strength_layout);
        strengthInput = findViewById(R.id.assessment_strength_input);
        nextStepLayout = findViewById(R.id.assessment_next_step_layout);
        nextStepInput = findViewById(R.id.assessment_next_step_input);
        saveButton = findViewById(R.id.assessment_save_button);
    }

    private void configureActions() {
        findViewById(R.id.assessment_retry_button).setOnClickListener(v -> loadAssessmentContext());
        saveButton.setOnClickListener(v -> performSave());
    }

    private void loadAssessmentContext() {
        int request = ++loadGeneration;
        showLoading();

        Task<Student> studentTask = studentRepository.loadStudent(studentId);
        Task<ClassOffering> classTask = classRepository.loadClass(classId);
        Task<Enrollment> enrollmentTask = enrollmentRepository.loadEnrollment(classId, studentId);

        Tasks.whenAllComplete(studentTask, classTask, enrollmentTask).addOnCompleteListener(task -> {
            if (!isCurrent(request)) return;
            if (!studentTask.isSuccessful()) { showError(studentTask.getException()); return; }
            if (!classTask.isSuccessful()) { showError(classTask.getException()); return; }
            if (!enrollmentTask.isSuccessful()) { showError(enrollmentTask.getException()); return; }

            student = studentTask.getResult();
            classOffering = classTask.getResult();
            enrollment = enrollmentTask.getResult();

            if (!classOffering.getTrackId().equals(enrollment.getTrackId())) {
                showError(new DataValidationException("Enrollment Track does not match its Class."));
                return;
            }

            trackRepository.loadTrack(enrollment.getTrackId()).addOnSuccessListener(loadedTrack -> {
                if (!isCurrent(request)) return;
                track = loadedTrack;
                renderLoadedContext();
            }).addOnFailureListener(this::showError);
        });
    }

    private void renderLoadedContext() {
        studentName.setText(student.getName());
        studentNickname.setText(student.getNickname());
        studentNickname.setVisibility(blank(student.getNickname()) ? View.GONE : View.VISIBLE);

        classContext.setText(String.format(Locale.getDefault(), "%s · %s (%s)",
                classOffering.getName(), track.getName(), track.getCode()));
        statusChip.setText(enrollment.getStatus());
        bindStatusChip(statusChip, enrollment.getStatus());

        if (!AssessmentRubricCatalog.isSupportedTrack(track.getTrackId())) {
            rubricLabel.setVisibility(View.GONE);
            showUnsupportedTrack();
            return;
        }

        rubricLabel.setVisibility(View.VISIBLE);
        rubricLabel.setText(AssessmentRubricCatalog.RUBRIC_ID_SS1);

        AssessmentData assessmentData = enrollment.getParsedAssessment();
        boolean isEditable = Enrollment.STATUS_ACTIVE.equals(enrollment.getStatus());

        if (assessmentData == null && !isEditable) {
            readOnlyBanner.setVisibility(View.VISIBLE);
            readOnlyBanner.setText(R.string.no_assessment_recorded);
            saveButton.setVisibility(View.GONE);
            showContent();
            return;
        }

        readOnlyBanner.setVisibility(isEditable ? View.GONE : View.VISIBLE);
        readOnlyBanner.setText(R.string.assessment_read_only_history);

        currentCriteria.clear();
        for (String key : AssessmentRubricCatalog.getApprovedCriteriaKeys()) {
            currentCriteria.put(key, AssessmentData.VALUE_NOT_OBSERVED);
        }

        if (assessmentData != null) {
            if (!AssessmentRubricCatalog.RUBRIC_ID_SS1.equals(assessmentData.getRubricId())) {
                showError(new DataValidationException("Stored Assessment has incompatible rubric ID: "
                        + assessmentData.getRubricId()));
                return;
            }
            Map<String, Object> storedCriteria = assessmentData.getCriteria();
            if (storedCriteria.size() != AssessmentRubricCatalog.getApprovedCriteriaKeys().size()) {
                showError(new DataValidationException("Stored Assessment criteria count is invalid."));
                return;
            }
            for (String key : AssessmentRubricCatalog.getApprovedCriteriaKeys()) {
                if (storedCriteria.containsKey(key)) {
                    Object val = storedCriteria.get(key);
                    if (AssessmentRubricCatalog.isValidCriterionValue(val)) {
                        currentCriteria.put(key, val);
                    } else {
                        showError(new DataValidationException("Stored Assessment has invalid criterion value for " + key));
                        return;
                    }
                } else {
                    showError(new DataValidationException("Stored Assessment is missing required criterion: " + key));
                    return;
                }
            }
            strengthInput.setText(assessmentData.getStrength());
            nextStepInput.setText(assessmentData.getNextStep());
        } else {
            strengthInput.setText("");
            nextStepInput.setText("");
        }

        buildDomainSections(isEditable);
        recalculateAndRefreshUI();

        setEditorEnabled(isEditable);
        saveButton.setVisibility(isEditable ? View.VISIBLE : View.GONE);
        showContent();
    }

    private void buildDomainSections(boolean isEditable) {
        domainsContainer.removeAllViews();
        criterionInputs.clear();
        domainScoreViews.clear();

        LayoutInflater inflater = LayoutInflater.from(this);
        List<AssessmentRubricCatalog.DomainDef> domains =
                AssessmentRubricCatalog.getDomainsForTrack(AssessmentRubricCatalog.TRACK_ID_SS1);

        for (AssessmentRubricCatalog.DomainDef domain : domains) {
            View domainCard = inflater.inflate(R.layout.dialog_class_editor, domainsContainer, false);
            // We use a clean programmatic card or container for each domain
            com.google.android.material.card.MaterialCardView card =
                    new com.google.android.material.card.MaterialCardView(this);
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));
            card.setRadius(dp(12));
            card.setStrokeColor(ContextCompat.getColor(this, R.color.slate_light));
            card.setStrokeWidth((int) dp(1));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = (int) dp(12);
            card.setLayoutParams(cardParams);

            LinearLayout domainLayout = new LinearLayout(this);
            domainLayout.setOrientation(LinearLayout.VERTICAL);
            domainLayout.setPadding((int) dp(16), (int) dp(14), (int) dp(16), (int) dp(14));

            // Domain Header row
            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView titleView = new TextView(this);
            titleView.setText(domain.getTitle());
            titleView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            titleView.setTextColor(ContextCompat.getColor(this, R.color.indigo_primary_dark));
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            titleView.setLayoutParams(titleParams);
            headerRow.addView(titleView);

            TextView scoreBadge = new TextView(this);
            scoreBadge.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            scoreBadge.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
            headerRow.addView(scoreBadge);
            domainScoreViews.put(domain.getId(), scoreBadge);

            domainLayout.addView(headerRow);

            // Add each criterion row
            for (AssessmentRubricCatalog.CriterionDef criterion : domain.getCriteria()) {
                View criterionRow = inflater.inflate(R.layout.item_assessment_criterion, domainLayout, false);
                TextView labelView = criterionRow.findViewById(R.id.criterion_label);
                labelView.setText(String.format(Locale.getDefault(), "%s %s", criterion.getId(), criterion.getLabel()));

                TextView guidanceView = criterionRow.findViewById(R.id.criterion_guidance);
                if (criterion.getObservationGuidance() != null && !criterion.getObservationGuidance().trim().isEmpty()) {
                    guidanceView.setVisibility(View.VISIBLE);
                    guidanceView.setText(criterion.getObservationGuidance());
                } else {
                    guidanceView.setVisibility(View.GONE);
                }

                AutoCompleteTextView input = criterionRow.findViewById(R.id.criterion_score_input);
                List<AssessmentRubricCatalog.ValueOption> options = AssessmentRubricCatalog.getValueOptions();
                List<String> labels = new java.util.ArrayList<>();
                for (AssessmentRubricCatalog.ValueOption opt : options) {
                    labels.add(opt.getFullLabel());
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
                input.setAdapter(adapter);

                Object currentVal = currentCriteria.get(criterion.getId());
                input.setText(labelForValue(currentVal), false);

                input.setOnItemClickListener((parent, view, position, id) -> {
                    AssessmentRubricCatalog.ValueOption selectedOpt = options.get(position);
                    currentCriteria.put(criterion.getId(), selectedOpt.getValue());
                    recalculateAndRefreshUI();
                });

                criterionInputs.put(criterion.getId(), input);
                domainLayout.addView(criterionRow);
            }

            card.addView(domainLayout);
            domainsContainer.addView(card);
        }
    }

    private void recalculateAndRefreshUI() {
        AssessmentCalculation calc = AssessmentCalculator.calculate(currentCriteria);

        // Update overall score and level
        if (calc.isOverallAvailable() && calc.getOverallScore() != null) {
            overallScoreText.setText(getString(R.string.overall_score_format,
                    calc.getFormattedOverallScore()));
            if (calc.getLevel() != null) {
                levelChip.setVisibility(View.VISIBLE);
                levelChip.setText(getString(R.string.level_format, calc.getLevel()));
                bindLevelChip(levelChip, calc.getLevel());
            } else {
                levelChip.setVisibility(View.GONE);
            }
        } else {
            overallScoreText.setText(getString(R.string.overall_score_format,
                    getString(R.string.not_enough_evidence)));
            levelChip.setVisibility(View.GONE);
        }

        // Update Radar chart
        radarChart.setDomainResults(calc.getDomainResults());

        // Update domain badge text and summary list
        domainScoresSummary.removeAllViews();
        for (DomainResult dr : calc.getDomainResults()) {
            TextView badge = domainScoreViews.get(dr.getDomainId());
            if (badge != null) {
                if (dr.isAvailable()) {
                    badge.setText(String.format(Locale.US, "%s · %s",
                            dr.getFormattedScore(), dr.getCoverageText()));
                    badge.setTextColor(ContextCompat.getColor(this, R.color.status_on_track));
                } else {
                    badge.setText(String.format(Locale.US, "%s (%s)",
                            getString(R.string.insufficient_evidence), dr.getCoverageText()));
                    badge.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
                }
            }

            TextView summaryLine = new TextView(this);
            summaryLine.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            summaryLine.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
            summaryLine.setPadding(0, (int) dp(2), 0, (int) dp(2));
            if (dr.isAvailable()) {
                summaryLine.setText(String.format(Locale.US, "• %s: %s (%s)",
                        dr.getDomainTitle(), dr.getFormattedScore(), dr.getCoverageText()));
            } else {
                summaryLine.setText(String.format(Locale.US, "• %s: %s (%s)",
                        dr.getDomainTitle(), getString(R.string.insufficient_evidence), dr.getCoverageText()));
            }
            domainScoresSummary.addView(summaryLine);
        }
    }

    private void performSave() {
        if (writeInFlight || !Enrollment.STATUS_ACTIVE.equals(enrollment.getStatus())) {
            return;
        }

        strengthLayout.setError(null);
        nextStepLayout.setError(null);

        String strength = text(strengthInput);
        String nextStep = text(nextStepInput);

        if (strength.length() > 500) {
            strengthLayout.setError(getString(R.string.strength_too_long));
            return;
        }
        if (nextStep.length() > 500) {
            nextStepLayout.setError(getString(R.string.next_step_too_long));
            return;
        }

        writeInFlight = true;
        setEditorEnabled(false);
        saveButton.setText(R.string.saving_assessment);

        AssessmentData toSave = new AssessmentData(
                AssessmentRubricCatalog.RUBRIC_ID_SS1,
                currentCriteria,
                strength,
                nextStep,
                null
        );

        assessmentRepository.saveAssessment(classId, studentId, toSave)
                .addOnSuccessListener(ignored -> {
                    if (isFinishing() || isDestroyed()) return;
                    writeInFlight = false;
                    saveButton.setText(R.string.save_assessment);
                    setEditorEnabled(true);
                    Snackbar.make(root, R.string.assessment_saved, Snackbar.LENGTH_SHORT).show();
                    // Re-read confirmed assessment to guarantee backend alignment
                    loadAssessmentContext();
                })
                .addOnFailureListener(failure -> {
                    if (isFinishing() || isDestroyed()) return;
                    writeInFlight = false;
                    saveButton.setText(R.string.save_assessment);
                    setEditorEnabled(true);
                    Snackbar.make(root, FirestoreErrorMessages.forException(failure), Snackbar.LENGTH_LONG).show();
                });
    }

    private void setEditorEnabled(boolean enabled) {
        for (AutoCompleteTextView input : criterionInputs.values()) {
            input.setEnabled(enabled && !writeInFlight);
        }
        strengthInput.setEnabled(enabled && !writeInFlight);
        nextStepInput.setEnabled(enabled && !writeInFlight);
        saveButton.setEnabled(enabled && !writeInFlight);
    }

    private String labelForValue(Object val) {
        for (AssessmentRubricCatalog.ValueOption opt : AssessmentRubricCatalog.getValueOptions()) {
            if (opt.getValue().equals(val)) {
                return opt.getFullLabel();
            }
        }
        return "N/O — ยังไม่มีข้อมูลสังเกตเพียงพอ";
    }

    private void bindStatusChip(Chip chip, String status) {
        int bg = R.color.status_archived_container;
        int fg = R.color.slate_gray;
        if (Enrollment.STATUS_ACTIVE.equals(status)) {
            bg = R.color.status_active_container;
            fg = R.color.status_on_track;
        } else if (Enrollment.STATUS_COMPLETED.equals(status)) {
            bg = R.color.status_completed_container;
            fg = R.color.status_completed;
        }
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, bg)));
        chip.setTextColor(ContextCompat.getColor(this, fg));
    }

    private void bindLevelChip(Chip chip, String level) {
        int bg = R.color.status_archived_container;
        int fg = R.color.slate_gray;
        if (AssessmentCalculation.LEVEL_STRONG.equals(level)) {
            bg = R.color.status_active_container;
            fg = R.color.status_on_track;
        } else if (AssessmentCalculation.LEVEL_READY.equals(level)) {
            bg = R.color.status_completed_container;
            fg = R.color.status_completed;
        } else if (AssessmentCalculation.LEVEL_DEVELOPING.equals(level)) {
            bg = R.color.status_attention_container;
            fg = R.color.status_needs_attention;
        } else if (AssessmentCalculation.LEVEL_NEED_SUPPORT.equals(level)) {
            bg = R.color.status_blocked_container;
            fg = R.color.status_blocked;
        }
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, bg)));
        chip.setTextColor(ContextCompat.getColor(this, fg));
    }

    private void showLoading() {
        loading.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        unsupportedTrackView.setVisibility(View.GONE);
    }

    private void showContent() {
        loading.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        unsupportedTrackView.setVisibility(View.GONE);
    }

    private void showUnsupportedTrack() {
        loading.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        unsupportedTrackView.setVisibility(View.VISIBLE);
    }

    private void showError(Exception failure) {
        loading.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        unsupportedTrackView.setVisibility(View.GONE);
        errorMessage.setText(FirestoreErrorMessages.forException(failure));
    }

    private boolean isCurrent(int request) {
        return !isFinishing() && !isDestroyed() && request == loadGeneration;
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
