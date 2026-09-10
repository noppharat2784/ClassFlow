package th.ac.vu.classflow.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.DataValidationException;
import th.ac.vu.classflow.data.repository.EnrollmentRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.ui.adapter.EnrollmentManageAdapter;
import th.ac.vu.classflow.ui.model.EnrollmentCardItem;

public final class EnrollmentDialogFragment extends DialogFragment {

    public static final String RESULT_ENROLLMENT_CHANGED = "enrollment_changed";
    private static final String ARG_STUDENT_ID = "student_id";

    private final StudentRepository studentRepository = new StudentRepository();
    private final EnrollmentRepository enrollmentRepository = new EnrollmentRepository();
    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final List<ClassOption> eligibleOptions = new ArrayList<>();

    private View loading;
    private TextView error;
    private TextView studentName;
    private TextView empty;
    private RecyclerView list;
    private View createSection;
    private View newClassLayout;
    private AutoCompleteTextView newClass;
    private TextView noEligible;
    private MaterialButton add;
    private EnrollmentManageAdapter adapter;
    @Nullable private String selectedClassId;
    private int generation;
    private boolean inFlight;

    public static EnrollmentDialogFragment newInstance(String studentId) {
        EnrollmentDialogFragment fragment = new EnrollmentDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_STUDENT_ID, studentId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_enrollment, null, false);
        bind(view);
        adapter = new EnrollmentManageAdapter(this::saveStatus);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        add.setOnClickListener(ignored -> createEnrollment());
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.manage_enrollment)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    @Override public void onStart() {
        super.onStart();
        loadData();
    }

    @Override public void onDestroy() {
        generation++;
        super.onDestroy();
    }

    private void bind(View view) {
        loading = view.findViewById(R.id.enrollment_loading);
        error = view.findViewById(R.id.enrollment_error);
        studentName = view.findViewById(R.id.enrollment_student_name);
        empty = view.findViewById(R.id.enrollment_empty);
        list = view.findViewById(R.id.enrollment_list);
        createSection = view.findViewById(R.id.enrollment_create_section);
        newClassLayout = view.findViewById(R.id.enrollment_new_class_layout);
        newClass = view.findViewById(R.id.enrollment_new_class);
        noEligible = view.findViewById(R.id.enrollment_no_eligible);
        add = view.findViewById(R.id.enrollment_add);
    }

    private void loadData() {
        int request = ++generation;
        showLoading(true);
        String studentId = requireArguments().getString(ARG_STUDENT_ID, "");
        Task<Student> studentTask = studentRepository.loadStudent(studentId);
        Task<List<Enrollment>> enrollmentTask = enrollmentRepository.loadForStudent(studentId);
        Task<List<ClassOffering>> classTask = classRepository.loadAllClasses();
        Tasks.whenAllComplete(studentTask, enrollmentTask, classTask).addOnCompleteListener(ignored -> {
            if (!usable(request)) return;
            if (!studentTask.isSuccessful()) { showFailure(studentTask.getException()); return; }
            if (!enrollmentTask.isSuccessful()) { showFailure(enrollmentTask.getException()); return; }
            if (!classTask.isSuccessful()) { showFailure(classTask.getException()); return; }
            Student student = studentTask.getResult();
            List<Enrollment> enrollments = enrollmentTask.getResult();
            studentName.setText(student.getName());
            resolvePresentation(student, enrollments, classTask.getResult(), request);
        });
    }

    private void resolvePresentation(Student student, List<Enrollment> enrollments,
                                     List<ClassOffering> classes, int request) {
        List<Task<EnrollmentCardItem>> existingTasks = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            existingTasks.add(classRepository.loadClass(enrollment.getClassId())
                    .continueWithTask(classResult -> {
                        ClassOffering value = classResult.getResult();
                        if (!value.getTrackId().equals(enrollment.getTrackId())) {
                            throw new DataValidationException("Enrollment Track does not match its Class.");
                        }
                        return trackRepository.loadTrack(enrollment.getTrackId())
                                .continueWith(trackResult -> new EnrollmentCardItem(
                                        enrollment, value, trackResult.getResult()));
                    }));
        }
        Set<String> represented = new HashSet<>();
        for (Enrollment enrollment : enrollments) represented.add(enrollment.getClassId());
        List<Task<ClassOption>> optionTasks = new ArrayList<>();
        if (student.isActive()) {
            for (ClassOffering value : classes) {
                if (!represented.contains(value.getClassId())
                        && (ClassOffering.STATUS_ACTIVE.equals(value.getStatus())
                        || ClassOffering.STATUS_PLANNED.equals(value.getStatus()))) {
                    optionTasks.add(trackRepository.loadTrack(value.getTrackId())
                            .continueWith(trackResult -> new ClassOption(value, trackResult.getResult())));
                }
            }
        }
        List<Task<?>> allTasks = new ArrayList<>();
        allTasks.addAll(existingTasks);
        allTasks.addAll(optionTasks);
        Tasks.whenAllComplete(allTasks).addOnCompleteListener(ignored -> {
            if (!usable(request)) return;
            List<EnrollmentCardItem> items = new ArrayList<>();
            for (Task<EnrollmentCardItem> task : existingTasks) {
                if (!task.isSuccessful()) { showFailure(task.getException()); return; }
                items.add(task.getResult());
            }
            eligibleOptions.clear();
            for (Task<ClassOption> task : optionTasks) {
                if (!task.isSuccessful()) { showFailure(task.getException()); return; }
                eligibleOptions.add(task.getResult());
            }
            adapter.submitList(items);
            list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            configureCreate(student);
            showLoading(false);
        });
    }

    private void configureCreate(Student student) {
        createSection.setVisibility(View.VISIBLE);
        if (!student.isActive()) {
            newClassLayout.setVisibility(View.GONE);
            add.setVisibility(View.GONE);
            noEligible.setText(R.string.archived_enrollment_disabled);
            noEligible.setVisibility(View.VISIBLE);
            return;
        }
        newClassLayout.setVisibility(eligibleOptions.isEmpty() ? View.GONE : View.VISIBLE);
        add.setVisibility(eligibleOptions.isEmpty() ? View.GONE : View.VISIBLE);
        noEligible.setText(R.string.no_eligible_classes);
        noEligible.setVisibility(eligibleOptions.isEmpty() ? View.VISIBLE : View.GONE);
        List<String> labels = new ArrayList<>();
        for (ClassOption option : eligibleOptions) labels.add(option.label());
        newClass.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));
        newClass.setThreshold(0);
        selectedClassId = null;
        newClass.setText("", false);
        newClass.setOnClickListener(ignored -> newClass.showDropDown());
        newClass.setOnItemClickListener((parent, view, position, id) ->
                selectedClassId = eligibleOptions.get(position).classOffering.getClassId());
    }

    private void createEnrollment() {
        if (inFlight) return;
        if (selectedClassId == null) {
            showMessage("Select an eligible Class.");
            return;
        }
        setInFlight(true);
        enrollmentRepository.createEnrollment(studentId(), selectedClassId)
                .addOnSuccessListener(ignored -> finishWrite(R.string.enrollment_saved))
                .addOnFailureListener(this::writeFailed);
    }

    private void saveStatus(EnrollmentCardItem item, String status) {
        if (inFlight) return;
        if (status.equals(item.getEnrollment().getStatus())) return;
        inFlight = true;
        adapter.setBusy(item.getEnrollment().getEnrollmentId());
        enrollmentRepository.updateStatus(item.getEnrollment().getClassId(), studentId(), status)
                .addOnSuccessListener(ignored -> finishWrite(R.string.enrollment_saved))
                .addOnFailureListener(failure -> {
                    adapter.clearPending(item.getEnrollment().getEnrollmentId());
                    writeFailed(failure);
                });
    }

    private void finishWrite(int message) {
        if (!isAdded()) return;
        inFlight = false;
        adapter.setBusy(null);
        getParentFragmentManager().setFragmentResult(RESULT_ENROLLMENT_CHANGED, new Bundle());
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        loadData();
    }

    private void writeFailed(Exception failure) {
        if (!isAdded()) return;
        inFlight = false;
        adapter.setBusy(null);
        add.setEnabled(true);
        showFailure(failure);
    }

    private void setInFlight(boolean value) {
        inFlight = value;
        setCancelable(!value);
        add.setEnabled(!value);
        newClass.setEnabled(!value);
    }

    private void showLoading(boolean value) {
        loading.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        if (value) {
            error.setVisibility(View.GONE);
            studentName.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            createSection.setVisibility(View.GONE);
        } else {
            studentName.setVisibility(View.VISIBLE);
        }
    }

    private void showFailure(Exception failure) {
        showLoading(false);
        showMessage(FirestoreErrorMessages.forException(failure));
    }

    private void showMessage(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private String studentId() { return requireArguments().getString(ARG_STUDENT_ID, ""); }
    private boolean usable(int request) { return isAdded() && getDialog() != null && request == generation; }

    private static final class ClassOption {
        final ClassOffering classOffering;
        final Track track;
        ClassOption(ClassOffering classOffering, Track track) {
            this.classOffering = classOffering; this.track = track;
        }
        String label() {
            return String.format(Locale.getDefault(), "%s · %s · Week %d",
                    classOffering.getName(), track.getCode(), classOffering.getCurrentWeek());
        }
    }
}
