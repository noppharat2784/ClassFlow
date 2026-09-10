package th.ac.vu.classflow.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.EnrollmentRepository;
import th.ac.vu.classflow.data.repository.ProjectRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;

public final class ProjectEditorDialogFragment extends DialogFragment {

    public static final String RESULT_PROJECT_CHANGED = "project_editor_changed";
    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_INITIAL_CLASS_ID = "initial_class_id";

    private final ProjectRepository projectRepository = new ProjectRepository();
    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final StudentRepository studentRepository = new StudentRepository();
    private final EnrollmentRepository enrollmentRepository = new EnrollmentRepository();

    private final List<ClassOffering> classList = new ArrayList<>();
    private final List<Student> selectedMembers = new ArrayList<>();
    private final List<Student> eligibleStudents = new ArrayList<>();

    private TextInputLayout titleLayout;
    private TextInputEditText titleInput;
    private TextInputLayout teamNameLayout;
    private TextInputEditText teamNameInput;
    private TextInputLayout classLayout;
    private AutoCompleteTextView classInput;
    private TextView weekNotice;
    private ChipGroup memberChips;
    private Button addMemberButton;
    private View loading;
    private TextView error;
    private Button saveButton;
    private Button archiveButton;

    @Nullable private Project loadedProject;
    @Nullable private ClassOffering selectedClass;
    private boolean inFlight;

    public static ProjectEditorDialogFragment newCreate(@Nullable String initialClassId) {
        ProjectEditorDialogFragment fragment = new ProjectEditorDialogFragment();
        if (initialClassId != null) {
            Bundle args = new Bundle();
            args.putString(ARG_INITIAL_CLASS_ID, initialClassId);
            fragment.setArguments(args);
        }
        return fragment;
    }

    public static ProjectEditorDialogFragment newEdit(String projectId) {
        ProjectEditorDialogFragment fragment = new ProjectEditorDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_ID, projectId);
        fragment.setArguments(args);
        return fragment;
    }

    private boolean isEditMode() {
        return getArguments() != null && getArguments().containsKey(ARG_PROJECT_ID);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_project_editor, null, false);
        bind(view);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isEditMode() ? R.string.edit_project : R.string.add_project)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);

        if (isEditMode()) {
            builder.setNeutralButton(R.string.archive_project, null);
        }

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        saveButton.setOnClickListener(ignored -> validateAndSave());

        if (isEditMode()) {
            archiveButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (archiveButton != null) {
                archiveButton.setTextColor(requireContext().getColor(R.color.status_blocked));
                archiveButton.setOnClickListener(ignored -> confirmArchive());
            }
            loadProjectForEdit();
        } else {
            loadClassesForCreate();
        }
    }

    private void bind(View view) {
        titleLayout = view.findViewById(R.id.project_title_layout);
        titleInput = view.findViewById(R.id.project_title_input);
        teamNameLayout = view.findViewById(R.id.project_team_name_layout);
        teamNameInput = view.findViewById(R.id.project_team_name_input);
        classLayout = view.findViewById(R.id.project_class_layout);
        classInput = view.findViewById(R.id.project_class_input);
        weekNotice = view.findViewById(R.id.project_week_notice);
        memberChips = view.findViewById(R.id.project_member_chips);
        addMemberButton = view.findViewById(R.id.btn_add_member);
        loading = view.findViewById(R.id.project_editor_loading);
        error = view.findViewById(R.id.project_editor_error);

        addMemberButton.setOnClickListener(v -> showAddMemberPicker());
    }

    private void loadClassesForCreate() {
        setLoading(true);
        classRepository.loadAllClasses().addOnSuccessListener(classes -> {
            if (!usable()) return;
            classList.clear();
            for (ClassOffering c : classes) {
                if (!ClassOffering.STATUS_ARCHIVED.equals(c.getStatus())) {
                    classList.add(c);
                }
            }
            configureClassDropdown();
            setLoading(false);

            String initialClassId = getArguments() != null ? getArguments().getString(ARG_INITIAL_CLASS_ID) : null;
            if (initialClassId != null) {
                for (ClassOffering c : classList) {
                    if (initialClassId.equals(c.getClassId())) {
                        onClassSelected(c);
                        break;
                    }
                }
            }
        }).addOnFailureListener(this::loadFailed);
    }

    private void configureClassDropdown() {
        List<String> labels = new ArrayList<>();
        for (ClassOffering c : classList) {
            labels.add(c.getName() + " (" + c.getClassId() + ")");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, labels);
        classInput.setAdapter(adapter);
        classInput.setOnItemClickListener((parent, v, position, id) -> {
            if (position >= 0 && position < classList.size()) {
                onClassSelected(classList.get(position));
            }
        });
    }

    private void onClassSelected(ClassOffering classOffering) {
        selectedClass = classOffering;
        classInput.setText(classOffering.getName() + " (" + classOffering.getClassId() + ")", false);
        weekNotice.setText(getString(R.string.project_context_format,
                classOffering.getTrackId().toUpperCase(Locale.US),
                classOffering.getName(),
                classOffering.getCurrentWeek()));

        selectedMembers.clear();
        renderMemberChips();
        loadEligibleStudentsForClass(classOffering.getClassId());
    }

    private void loadEligibleStudentsForClass(String classId) {
        setLoading(true);
        enrollmentRepository.loadActiveForClass(classId).addOnSuccessListener(enrollments -> {
            if (!usable()) return;
            List<String> studentIds = new ArrayList<>();
            for (Enrollment e : enrollments) {
                studentIds.add(e.getStudentId());
            }

            if (studentIds.isEmpty()) {
                eligibleStudents.clear();
                setLoading(false);
                return;
            }

            List<Task<Student>> studentTasks = new ArrayList<>();
            for (String sid : studentIds) {
                studentTasks.add(studentRepository.loadStudent(sid));
            }

            Tasks.whenAllComplete(studentTasks).addOnCompleteListener(t -> {
                if (!usable()) return;
                eligibleStudents.clear();
                for (Task<Student> st : studentTasks) {
                    if (st.isSuccessful() && st.getResult() != null && st.getResult().isActive()) {
                        eligibleStudents.add(st.getResult());
                    }
                }
                setLoading(false);
            });
        }).addOnFailureListener(this::loadFailed);
    }

    private void loadProjectForEdit() {
        setLoading(true);
        String projectId = requireArguments().getString(ARG_PROJECT_ID, "");
        projectRepository.loadProject(projectId).addOnSuccessListener(project -> {
            if (!usable()) return;
            loadedProject = project;
            titleInput.setText(project.getTitle());
            if (project.getTeamName() != null) {
                teamNameInput.setText(project.getTeamName());
            }

            classRepository.loadClass(project.getClassId()).addOnSuccessListener(classOffering -> {
                if (!usable()) return;
                selectedClass = classOffering;
                classInput.setText(classOffering.getName() + " (" + classOffering.getClassId() + ")", false);
                classInput.setEnabled(false);
                classLayout.setEnabled(false);

                weekNotice.setText(getString(R.string.project_context_format,
                        project.getTrackId().toUpperCase(Locale.US),
                        classOffering.getName(),
                        project.getCurrentWeek()));

                loadExistingMembersAndEligibles(project);
            }).addOnFailureListener(this::loadFailed);
        }).addOnFailureListener(this::loadFailed);
    }

    private void loadExistingMembersAndEligibles(Project project) {
        List<String> memberIds = project.getMemberIds();
        List<Task<Student>> tasks = new ArrayList<>();
        for (String mid : memberIds) {
            tasks.add(studentRepository.loadStudent(mid));
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
            if (!usable()) return;
            selectedMembers.clear();
            for (Task<Student> st : tasks) {
                if (st.isSuccessful() && st.getResult() != null) {
                    selectedMembers.add(st.getResult());
                }
            }
            renderMemberChips();
            loadEligibleStudentsForClass(project.getClassId());
        });
    }

    private void renderMemberChips() {
        memberChips.removeAllViews();
        for (Student student : selectedMembers) {
            Chip chip = new Chip(requireContext());
            String displayName = (student.getNickname() != null && !student.getNickname().trim().isEmpty())
                    ? student.getNickname().trim() : student.getName();
            if (!student.isActive()) {
                displayName += " " + getString(R.string.member_archived_label);
            }
            chip.setText(displayName);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                if (selectedMembers.size() <= 1) {
                    showError("A project must have at least one member.");
                    return;
                }
                selectedMembers.remove(student);
                renderMemberChips();
            });
            memberChips.addView(chip);
        }
    }

    private void showAddMemberPicker() {
        if (selectedClass == null) {
            showError("Please select a class first.");
            return;
        }

        List<Student> candidates = new ArrayList<>();
        for (Student s : eligibleStudents) {
            boolean alreadySelected = false;
            for (Student selected : selectedMembers) {
                if (selected.getStudentId().equals(s.getStudentId())) {
                    alreadySelected = true;
                    break;
                }
            }
            if (!alreadySelected) {
                candidates.add(s);
            }
        }

        if (candidates.isEmpty()) {
            showError("No other eligible students found in this class.");
            return;
        }

        String[] names = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            Student c = candidates.get(i);
            String nick = (c.getNickname() != null && !c.getNickname().trim().isEmpty()) ? " (" + c.getNickname().trim() + ")" : "";
            names[i] = c.getName() + nick + " · " + c.getStudentId();
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.select_members)
                .setItems(names, (dialog, which) -> {
                    selectedMembers.add(candidates.get(which));
                    renderMemberChips();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void validateAndSave() {
        if (inFlight) return;
        clearError();

        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String teamName = teamNameInput.getText() != null ? teamNameInput.getText().toString().trim() : "";

        if (title.isEmpty()) {
            titleLayout.setError("Title is required.");
            return;
        }
        titleLayout.setError(null);

        if (selectedClass == null) {
            classLayout.setError("Class is required.");
            return;
        }
        classLayout.setError(null);

        if (selectedMembers.isEmpty()) {
            showError("At least one member is required.");
            return;
        }

        List<String> memberIds = new ArrayList<>();
        for (Student s : selectedMembers) {
            memberIds.add(s.getStudentId());
        }

        setLoading(true);
        inFlight = true;

        if (isEditMode()) {
            projectRepository.updateProject(loadedProject.getProjectId(), title, teamName, memberIds)
                    .addOnSuccessListener(unused -> finishSuccess())
                    .addOnFailureListener(this::saveFailed);
        } else {
            projectRepository.createProject(selectedClass.getClassId(), title, teamName, memberIds)
                    .addOnSuccessListener(newId -> finishSuccess())
                    .addOnFailureListener(this::saveFailed);
        }
    }

    private void confirmArchive() {
        if (loadedProject == null || inFlight) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.archive_project_title)
                .setMessage(R.string.archive_project_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.archive_project, (d, w) -> {
                    setLoading(true);
                    inFlight = true;
                    projectRepository.archiveProject(loadedProject.getProjectId())
                            .addOnSuccessListener(unused -> finishSuccess())
                            .addOnFailureListener(this::saveFailed);
                })
                .show();
    }

    private void finishSuccess() {
        if (!usable()) return;
        getParentFragmentManager().setFragmentResult(RESULT_PROJECT_CHANGED, new Bundle());
        dismiss();
    }

    private void saveFailed(Exception e) {
        if (!usable()) return;
        setLoading(false);
        inFlight = false;
        showError(FirestoreErrorMessages.forException(e));
    }

    private void loadFailed(Exception e) {
        if (!usable()) return;
        setLoading(false);
        showError(FirestoreErrorMessages.forException(e));
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        error.setText("");
        error.setVisibility(View.GONE);
    }

    private void setLoading(boolean isLoading) {
        loading.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
        if (saveButton != null) saveButton.setEnabled(!isLoading);
        if (archiveButton != null) archiveButton.setEnabled(!isLoading);
        addMemberButton.setEnabled(!isLoading);
    }

    private boolean usable() {
        return isAdded() && getDialog() != null && getDialog().isShowing();
    }
}
