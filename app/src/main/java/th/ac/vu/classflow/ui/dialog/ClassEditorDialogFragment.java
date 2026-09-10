package th.ac.vu.classflow.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.firebase.Timestamp;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;

public final class ClassEditorDialogFragment extends DialogFragment {

    public static final String RESULT_CLASS_CHANGED = "class_editor_changed";
    private static final String ARG_CLASS_ID = "class_id";
    private static final String STATE_START_DATE = "start_date";

    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final List<Track> tracks = new ArrayList<>();
    private final List<CurriculumWeek> weeks = new ArrayList<>();

    private TextInputLayout classIdLayout;
    private TextInputLayout nameLayout;
    private TextInputLayout trackLayout;
    private TextInputLayout weekLayout;
    private TextInputLayout statusLayout;
    private TextInputLayout startDateLayout;
    private TextInputEditText classIdInput;
    private TextInputEditText nameInput;
    private AutoCompleteTextView trackInput;
    private AutoCompleteTextView weekInput;
    private AutoCompleteTextView statusInput;
    private TextInputEditText startDateInput;
    private View loading;
    private View lifecycleActions;
    private TextView error;
    private MaterialButton activate;
    private MaterialButton complete;
    private MaterialButton archive;
    private android.widget.Button saveButton;

    @Nullable
    private Track selectedTrack;
    @Nullable
    private CurriculumWeek selectedWeek;
    @Nullable
    private Timestamp selectedStartDate;
    @Nullable
    private ClassOffering loadedClass;
    private String selectedStatus = ClassOffering.STATUS_PLANNED;
    private boolean inFlight;
    private int weekLoadGeneration;

    public static ClassEditorDialogFragment newCreate() {
        return new ClassEditorDialogFragment();
    }

    public static ClassEditorDialogFragment newEdit(String classId) {
        ClassEditorDialogFragment fragment = new ClassEditorDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_CLASS_ID, classId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater()
                .inflate(R.layout.dialog_class_editor, null, false);
        bindViews(view);
        configureStaticControls();

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_START_DATE)) {
            selectedStartDate = new Timestamp(
                    savedInstanceState.getLong(STATE_START_DATE), 0);
            renderStartDate();
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isEditMode() ? R.string.edit_class : R.string.add_class)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        saveButton.setOnClickListener(ignored -> validateAndSave());
        initializeData();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (selectedStartDate != null) {
            outState.putLong(STATE_START_DATE, selectedStartDate.getSeconds());
        }
        super.onSaveInstanceState(outState);
    }

    private void bindViews(View view) {
        classIdLayout = view.findViewById(R.id.class_id_layout);
        nameLayout = view.findViewById(R.id.class_name_layout);
        trackLayout = view.findViewById(R.id.class_track_layout);
        weekLayout = view.findViewById(R.id.class_week_layout);
        statusLayout = view.findViewById(R.id.class_status_layout);
        startDateLayout = view.findViewById(R.id.class_start_date_layout);
        classIdInput = view.findViewById(R.id.class_id_input);
        nameInput = view.findViewById(R.id.class_name_input);
        trackInput = view.findViewById(R.id.class_track_input);
        weekInput = view.findViewById(R.id.class_week_input);
        statusInput = view.findViewById(R.id.class_status_input);
        startDateInput = view.findViewById(R.id.class_start_date_input);
        loading = view.findViewById(R.id.class_editor_loading);
        lifecycleActions = view.findViewById(R.id.class_lifecycle_actions);
        error = view.findViewById(R.id.class_editor_error);
        activate = view.findViewById(R.id.class_activate);
        complete = view.findViewById(R.id.class_complete);
        archive = view.findViewById(R.id.class_archive);
    }

    private void configureStaticControls() {
        configureStatusOptions(Arrays.asList(
                ClassOffering.STATUS_PLANNED,
                ClassOffering.STATUS_ACTIVE,
                ClassOffering.STATUS_COMPLETED,
                ClassOffering.STATUS_ARCHIVED
        ), selectedStatus);

        View.OnClickListener dateListener = ignored -> showDatePicker();
        startDateInput.setOnClickListener(dateListener);
        startDateLayout.setEndIconOnClickListener(dateListener);

        activate.setOnClickListener(ignored -> confirmLifecycle(ClassOffering.STATUS_ACTIVE));
        complete.setOnClickListener(ignored -> confirmLifecycle(ClassOffering.STATUS_COMPLETED));
        archive.setOnClickListener(ignored -> confirmLifecycle(ClassOffering.STATUS_ARCHIVED));
    }

    private void configureStatusOptions(List<String> statuses, String initialStatus) {
        statusInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, statuses));
        selectedStatus = initialStatus;
        statusInput.setText(initialStatus, false);
        statusInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedStatus = statuses.get(position);
            statusLayout.setError(null);
        });
    }

    private void initializeData() {
        setFormLoading(true);
        if (isEditMode()) {
            loadExistingClass();
        } else {
            loadCreateTracks();
        }
    }

    private void loadCreateTracks() {
        trackRepository.loadActiveTracks()
                .addOnSuccessListener(result -> {
                    if (!isUsable()) {
                        return;
                    }
                    tracks.clear();
                    tracks.addAll(result);
                    if (tracks.isEmpty()) {
                        showEditorError(getString(R.string.no_tracks_available));
                        setFormLoading(false);
                        saveButton.setEnabled(false);
                        return;
                    }
                    configureTrackDropdown();
                    selectTrack(tracks.get(0), 1);
                })
                .addOnFailureListener(this::showLoadFailure);
    }

    private void loadExistingClass() {
        String classId = requireArguments().getString(ARG_CLASS_ID, "");
        classRepository.loadClass(classId)
                .addOnSuccessListener(classOffering -> {
                    if (!isUsable()) {
                        return;
                    }
                    loadedClass = classOffering;
                    classIdInput.setText(classOffering.getClassId());
                    nameInput.setText(classOffering.getName());
                    configureEditStatusAndActions(classOffering.getStatus());
                    if (selectedStartDate == null) {
                        selectedStartDate = classOffering.getStartDate();
                        renderStartDate();
                    }
                    classIdInput.setEnabled(false);
                    trackInput.setEnabled(false);
                    trackRepository.loadTrack(classOffering.getTrackId())
                            .addOnSuccessListener(track -> {
                                if (!isUsable()) {
                                    return;
                                }
                                tracks.clear();
                                tracks.add(track);
                                selectedTrack = track;
                                trackInput.setText(trackLabel(track), false);
                                loadWeeks(track, classOffering.getCurrentWeek());
                            })
                            .addOnFailureListener(this::showLoadFailure);
                })
                .addOnFailureListener(this::showLoadFailure);
    }

    private void configureEditStatusAndActions(String currentStatus) {
        if (ClassOffering.STATUS_PLANNED.equals(currentStatus)) {
            configureStatusOptions(Arrays.asList(
                    ClassOffering.STATUS_PLANNED,
                    ClassOffering.STATUS_ACTIVE,
                    ClassOffering.STATUS_ARCHIVED), currentStatus);
        } else if (ClassOffering.STATUS_ACTIVE.equals(currentStatus)) {
            configureStatusOptions(Arrays.asList(
                    ClassOffering.STATUS_ACTIVE,
                    ClassOffering.STATUS_COMPLETED,
                    ClassOffering.STATUS_ARCHIVED), currentStatus);
        } else if (ClassOffering.STATUS_COMPLETED.equals(currentStatus)) {
            configureStatusOptions(Arrays.asList(
                    ClassOffering.STATUS_COMPLETED,
                    ClassOffering.STATUS_ACTIVE,
                    ClassOffering.STATUS_ARCHIVED), currentStatus);
        } else {
            configureStatusOptions(java.util.Collections.singletonList(
                    ClassOffering.STATUS_ARCHIVED), currentStatus);
        }

        boolean planned = ClassOffering.STATUS_PLANNED.equals(currentStatus);
        boolean activeStatus = ClassOffering.STATUS_ACTIVE.equals(currentStatus);
        boolean completedStatus = ClassOffering.STATUS_COMPLETED.equals(currentStatus);
        boolean archivedStatus = ClassOffering.STATUS_ARCHIVED.equals(currentStatus);
        activate.setVisibility(planned || completedStatus ? View.VISIBLE : View.GONE);
        activate.setText(completedStatus ? R.string.reactivate : R.string.activate);
        complete.setVisibility(activeStatus ? View.VISIBLE : View.GONE);
        archive.setVisibility(archivedStatus ? View.GONE : View.VISIBLE);
        lifecycleActions.setVisibility(archivedStatus ? View.GONE : View.VISIBLE);
    }

    private void configureTrackDropdown() {
        List<String> labels = new ArrayList<>();
        for (Track track : tracks) {
            labels.add(trackLabel(track));
        }
        trackInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));
        trackInput.setOnItemClickListener((parent, view, position, id) -> {
            trackLayout.setError(null);
            selectTrack(tracks.get(position), 1);
        });
    }

    private void selectTrack(Track track, int preferredWeek) {
        selectedTrack = track;
        selectedWeek = null;
        trackInput.setText(trackLabel(track), false);
        loadWeeks(track, preferredWeek);
    }

    private void loadWeeks(Track track, int preferredWeek) {
        int generation = ++weekLoadGeneration;
        setFormLoading(true);
        trackRepository.loadWeeks(track.getTrackId())
                .addOnSuccessListener(result -> {
                    if (!isUsable() || generation != weekLoadGeneration
                            || selectedTrack == null
                            || !track.getTrackId().equals(selectedTrack.getTrackId())) {
                        return;
                    }
                    weeks.clear();
                    weeks.addAll(result);
                    if (weeks.isEmpty()) {
                        showEditorError("The selected Track has no curriculum Weeks.");
                        setFormLoading(false);
                        saveButton.setEnabled(false);
                        return;
                    }
                    List<String> labels = new ArrayList<>();
                    for (CurriculumWeek week : weeks) {
                        labels.add(weekLabel(week));
                    }
                    weekInput.setAdapter(new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_list_item_1, labels));
                    weekInput.setOnItemClickListener((parent, view, position, id) -> {
                        selectedWeek = weeks.get(position);
                        weekLayout.setError(null);
                    });
                    CurriculumWeek initial = weeks.get(0);
                    for (CurriculumWeek week : weeks) {
                        if (week.getWeekNumber() == preferredWeek) {
                            initial = week;
                            break;
                        }
                    }
                    selectedWeek = initial;
                    weekInput.setText(weekLabel(initial), false);
                    clearEditorError();
                    setFormLoading(false);
                })
                .addOnFailureListener(failure -> {
                    if (generation == weekLoadGeneration) {
                        showLoadFailure(failure);
                    }
                });
    }

    private void validateAndSave() {
        if (inFlight) {
            return;
        }
        clearFieldErrors();
        String classId = textOf(classIdInput).trim();
        String name = textOf(nameInput).trim();
        boolean valid = true;
        if (classId.isEmpty()) {
            classIdLayout.setError("Class ID is required.");
            valid = false;
        }
        if (name.isEmpty()) {
            nameLayout.setError("Class name is required.");
            valid = false;
        }
        if (selectedTrack == null) {
            trackLayout.setError("Track is required.");
            valid = false;
        }
        if (selectedWeek == null) {
            weekLayout.setError("Current Week is required.");
            valid = false;
        }
        if (!ClassOffering.isValidStatus(selectedStatus)) {
            statusLayout.setError("Select a valid Class status.");
            valid = false;
        }
        if (selectedStartDate == null) {
            startDateLayout.setError("Start Date is required.");
            valid = false;
        }
        if (!valid) {
            return;
        }

        if (loadedClass != null
                && ((ClassOffering.STATUS_ARCHIVED.equals(selectedStatus)
                && !ClassOffering.STATUS_ARCHIVED.equals(loadedClass.getStatus()))
                || (ClassOffering.STATUS_ACTIVE.equals(loadedClass.getStatus())
                && ClassOffering.STATUS_COMPLETED.equals(selectedStatus)))) {
            confirmThenSave(classId, name);
        } else {
            saveClass(classId, name);
        }
    }

    private void confirmThenSave(String classId, String name) {
        int title = ClassOffering.STATUS_ARCHIVED.equals(selectedStatus)
                ? R.string.archive_class_title : R.string.mark_completed_title;
        int action = ClassOffering.STATUS_ARCHIVED.equals(selectedStatus)
                ? R.string.archive : R.string.mark_completed;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(action, (dialog, which) -> saveClass(classId, name))
                .show();
    }

    private void saveClass(String classId, String name) {
        setInFlight(true);
        if (loadedClass == null) {
            ClassOffering classOffering = new ClassOffering(
                    classId,
                    name,
                    selectedTrack.getTrackId(),
                    selectedWeek.getWeekNumber(),
                    selectedStatus,
                    selectedStartDate
            );
            classRepository.createClass(classOffering)
                    .addOnSuccessListener(ignored -> finishSuccess())
                    .addOnFailureListener(this::finishWriteFailure);
        } else {
            classRepository.updateClassMetadata(
                            loadedClass.getClassId(),
                            name,
                            selectedWeek.getWeekNumber(),
                            selectedStatus,
                            selectedStartDate)
                    .addOnSuccessListener(ignored -> finishSuccess())
                    .addOnFailureListener(this::finishWriteFailure);
        }
    }

    private void confirmLifecycle(String status) {
        if (loadedClass == null || inFlight) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_status_title)
                .setMessage(getString(R.string.confirm_status_message, status))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(status.equals(ClassOffering.STATUS_ARCHIVED)
                                ? R.string.archive : R.string.save,
                        (dialog, which) -> updateLifecycle(status))
                .show();
    }

    private void updateLifecycle(String status) {
        setInFlight(true);
        classRepository.updateLifecycleStatus(loadedClass.getClassId(), status)
                .addOnSuccessListener(ignored -> finishSuccess())
                .addOnFailureListener(this::finishWriteFailure);
    }

    private void showDatePicker() {
        if (inFlight) {
            return;
        }
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.select_start_date);
        if (selectedStartDate != null) {
            builder.setSelection(selectedStartDate.toDate().getTime());
        }
        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            selectedStartDate = new Timestamp(new Date(selection));
            startDateLayout.setError(null);
            renderStartDate();
        });
        picker.show(getParentFragmentManager(), "class_start_date_picker");
    }

    private void renderStartDate() {
        if (selectedStartDate != null) {
            DateFormat formatter = android.text.format.DateFormat.getMediumDateFormat(
                    requireContext());
            startDateInput.setText(formatter.format(selectedStartDate.toDate()));
        }
    }

    private void setFormLoading(boolean isLoading) {
        loading.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
        if (saveButton != null) {
            saveButton.setEnabled(!isLoading && !inFlight);
        }
        nameInput.setEnabled(!isLoading && !inFlight);
        weekInput.setEnabled(!isLoading && !inFlight);
        statusInput.setEnabled(!isLoading && !inFlight);
        startDateInput.setEnabled(!isLoading && !inFlight);
        trackInput.setEnabled(!isLoading && !inFlight && !isEditMode());
        classIdInput.setEnabled(!isLoading && !inFlight && !isEditMode());
        activate.setEnabled(!isLoading && !inFlight);
        complete.setEnabled(!isLoading && !inFlight);
        archive.setEnabled(!isLoading && !inFlight);
    }

    private void setInFlight(boolean active) {
        inFlight = active;
        setCancelable(!active);
        setFormLoading(active);
    }

    private void showLoadFailure(Exception failure) {
        if (!isUsable()) {
            return;
        }
        setFormLoading(false);
        saveButton.setEnabled(false);
        showEditorError(FirestoreErrorMessages.forException(failure));
    }

    private void finishWriteFailure(Exception failure) {
        if (!isUsable()) {
            return;
        }
        setInFlight(false);
        showEditorError(FirestoreErrorMessages.forException(failure));
    }

    private void finishSuccess() {
        if (!isAdded()) {
            return;
        }
        getParentFragmentManager().setFragmentResult(RESULT_CLASS_CHANGED, new Bundle());
        dismissAllowingStateLoss();
    }

    private void clearFieldErrors() {
        classIdLayout.setError(null);
        nameLayout.setError(null);
        trackLayout.setError(null);
        weekLayout.setError(null);
        statusLayout.setError(null);
        startDateLayout.setError(null);
        clearEditorError();
    }

    private void showEditorError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void clearEditorError() {
        error.setText(null);
        error.setVisibility(View.GONE);
    }

    private boolean isEditMode() {
        return getArguments() != null && getArguments().containsKey(ARG_CLASS_ID);
    }

    private boolean isUsable() {
        return isAdded() && getDialog() != null;
    }

    private String trackLabel(Track track) {
        return String.format(Locale.getDefault(), "%s · %s", track.getCode(), track.getName());
    }

    private String weekLabel(CurriculumWeek week) {
        return String.format(Locale.US, "W%02d · %s", week.getWeekNumber(), week.getTitle());
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
