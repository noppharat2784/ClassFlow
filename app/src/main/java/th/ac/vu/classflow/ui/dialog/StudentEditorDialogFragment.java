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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;

public final class StudentEditorDialogFragment extends DialogFragment {

    public static final String RESULT_STUDENT_CHANGED = "student_editor_changed";
    private static final String ARG_STUDENT_ID = "student_id";

    private final StudentRepository studentRepository = new StudentRepository();
    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final List<ClassOption> classOptions = new ArrayList<>();

    private TextInputLayout idLayout;
    private TextInputLayout nameLayout;
    private TextInputLayout nicknameLayout;
    private View initialClassLayout;
    private TextInputEditText idInput;
    private TextInputEditText nameInput;
    private TextInputEditText nicknameInput;
    private AutoCompleteTextView initialClassInput;
    private View loading;
    private TextView error;
    private android.widget.Button save;
    @Nullable private Student loadedStudent;
    @Nullable private String selectedClassId;
    private boolean inFlight;

    public static StudentEditorDialogFragment newCreate() { return new StudentEditorDialogFragment(); }

    public static StudentEditorDialogFragment newEdit(String studentId) {
        StudentEditorDialogFragment fragment = new StudentEditorDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_STUDENT_ID, studentId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_student_editor, null, false);
        bind(view);
        initialClassLayout.setVisibility(isEditMode() ? View.GONE : View.VISIBLE);
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isEditMode() ? R.string.edit_student : R.string.add_student)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
    }

    @Override public void onStart() {
        super.onStart();
        save = ((AlertDialog) requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(ignored -> validateAndSave());
        if (isEditMode()) loadStudent(); else loadClassOptions();
    }

    private void bind(View view) {
        idLayout = view.findViewById(R.id.student_id_layout);
        nameLayout = view.findViewById(R.id.student_name_layout);
        nicknameLayout = view.findViewById(R.id.student_nickname_layout);
        initialClassLayout = view.findViewById(R.id.student_initial_class_layout);
        idInput = view.findViewById(R.id.student_id_input);
        nameInput = view.findViewById(R.id.student_name_input);
        nicknameInput = view.findViewById(R.id.student_nickname_input);
        initialClassInput = view.findViewById(R.id.student_initial_class_input);
        loading = view.findViewById(R.id.student_editor_loading);
        error = view.findViewById(R.id.student_editor_error);
    }

    private void loadStudent() {
        setLoading(true);
        studentRepository.loadStudent(requireArguments().getString(ARG_STUDENT_ID, ""))
                .addOnSuccessListener(student -> {
                    if (!usable()) return;
                    loadedStudent = student;
                    idInput.setText(student.getStudentId());
                    idInput.setEnabled(false);
                    nameInput.setText(student.getName());
                    nicknameInput.setText(student.getNickname());
                    setLoading(false);
                }).addOnFailureListener(this::loadFailed);
    }

    private void loadClassOptions() {
        setLoading(true);
        classRepository.loadAllClasses().addOnSuccessListener(classes -> {
            if (!usable()) return;
            List<Task<ClassOption>> tasks = new ArrayList<>();
            for (ClassOffering value : classes) {
                if (ClassOffering.STATUS_ACTIVE.equals(value.getStatus())
                        || ClassOffering.STATUS_PLANNED.equals(value.getStatus())) {
                    tasks.add(trackRepository.loadTrack(value.getTrackId())
                            .continueWith(task -> new ClassOption(value, task.getResult())));
                }
            }
            Tasks.whenAllComplete(tasks).addOnCompleteListener(ignored -> {
                if (!usable()) return;
                classOptions.clear();
                for (Task<ClassOption> task : tasks) {
                    if (!task.isSuccessful()) { loadFailed(task.getException()); return; }
                    classOptions.add(task.getResult());
                }
                configureClassDropdown();
                setLoading(false);
            });
        }).addOnFailureListener(this::loadFailed);
    }

    private void configureClassDropdown() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.no_initial_class));
        for (ClassOption option : classOptions) labels.add(option.label());
        initialClassInput.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));
        initialClassInput.setThreshold(0);
        initialClassInput.setText(labels.get(0), false);
        initialClassInput.setOnClickListener(ignored -> {
            initialClassInput.setText("", false);
            initialClassInput.showDropDown();
        });
        initialClassInput.setOnDismissListener(() -> {
            if (selectedClassId == null && initialClassInput.getText().length() == 0) {
                initialClassInput.setText(getString(R.string.no_initial_class), false);
            }
        });
        selectedClassId = null;
        initialClassInput.setOnItemClickListener((parent, view, position, id) ->
                selectedClassId = position == 0 ? null
                        : classOptions.get(position - 1).classOffering.getClassId());
    }

    private void validateAndSave() {
        if (inFlight) return;
        idLayout.setError(null); nameLayout.setError(null); nicknameLayout.setError(null);
        error.setVisibility(View.GONE);
        String id = text(idInput).trim();
        String name = text(nameInput).trim();
        String nickname = text(nicknameInput).trim();
        boolean valid = true;
        if (id.isEmpty()) { idLayout.setError("Student ID is required."); valid = false; }
        else if (id.contains("/")) { idLayout.setError("Student ID cannot contain a slash."); valid = false; }
        if (name.isEmpty() || name.length() > 80) {
            nameLayout.setError("Name must be 1–80 characters."); valid = false;
        }
        if (nickname.length() > 40) {
            nicknameLayout.setError("Nickname must be 40 characters or fewer."); valid = false;
        }
        if (!valid) return;
        setInFlight(true);
        Task<Void> task = loadedStudent == null
                ? studentRepository.createStudent(new Student(id, name, nickname, true), selectedClassId)
                : studentRepository.updateProfile(loadedStudent.getStudentId(), name, nickname);
        task.addOnSuccessListener(ignored -> finishSuccess())
                .addOnFailureListener(this::writeFailed);
    }

    private void setLoading(boolean value) {
        loading.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        if (save != null) save.setEnabled(!value && !inFlight);
        idInput.setEnabled(!value && !inFlight && !isEditMode());
        nameInput.setEnabled(!value && !inFlight);
        nicknameInput.setEnabled(!value && !inFlight);
        initialClassInput.setEnabled(!value && !inFlight);
    }

    private void setInFlight(boolean value) {
        inFlight = value;
        setCancelable(!value);
        setLoading(value);
    }

    private void loadFailed(Exception failure) {
        if (!usable()) return;
        setLoading(false);
        save.setEnabled(false);
        showError(failure);
    }

    private void writeFailed(Exception failure) {
        if (!usable()) return;
        setInFlight(false);
        showError(failure);
    }

    private void showError(Exception failure) {
        error.setText(FirestoreErrorMessages.forException(failure));
        error.setVisibility(View.VISIBLE);
    }

    private void finishSuccess() {
        if (!isAdded()) return;
        getParentFragmentManager().setFragmentResult(RESULT_STUDENT_CHANGED, new Bundle());
        dismissAllowingStateLoss();
    }

    private boolean isEditMode() { return getArguments() != null && getArguments().containsKey(ARG_STUDENT_ID); }
    private boolean usable() { return isAdded() && getDialog() != null; }
    private String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }

    private static final class ClassOption {
        final ClassOffering classOffering;
        final Track track;
        ClassOption(ClassOffering classOffering, Track track) {
            this.classOffering = classOffering; this.track = track;
        }
        String label() {
            return String.format(Locale.getDefault(), "%s · %s · %s",
                    classOffering.getName(), track.getCode(), track.getName());
        }
    }
}
