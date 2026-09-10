package th.ac.vu.classflow.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.bootstrap.CurriculumCatalog;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.StudentProgress;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.EnrollmentRepository;
import th.ac.vu.classflow.data.repository.ProjectProgressRepository;
import th.ac.vu.classflow.data.repository.ProjectRepository;
import th.ac.vu.classflow.data.repository.StudentProgressRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.ui.activity.ClassDetailActivity;
import th.ac.vu.classflow.ui.activity.MainActivity;
import th.ac.vu.classflow.ui.activity.ManageClassesActivity;
import th.ac.vu.classflow.ui.activity.ProjectDetailActivity;
import th.ac.vu.classflow.ui.activity.StudentDetailActivity;
import th.ac.vu.classflow.ui.adapter.ClassCardAdapter;
import th.ac.vu.classflow.ui.adapter.DashboardInterventionAdapter;
import th.ac.vu.classflow.ui.model.DashboardClassSummary;
import th.ac.vu.classflow.ui.model.DashboardDerivationHelper;
import th.ac.vu.classflow.ui.model.DashboardItem;

public final class HomeFragment extends Fragment {

    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final EnrollmentRepository enrollmentRepository = new EnrollmentRepository();
    private final StudentProgressRepository studentProgressRepository = new StudentProgressRepository();
    private final StudentRepository studentRepository = new StudentRepository();
    private final ProjectRepository projectRepository = new ProjectRepository();
    private final ProjectProgressRepository projectProgressRepository = new ProjectProgressRepository();

    private ClassCardAdapter classAdapter;
    private DashboardInterventionAdapter interventionAdapter;

    private NestedScrollView homeScrollView;
    private RecyclerView classList;
    private RecyclerView interventionList;
    private ChipGroup feedFilterGroup;
    private Chip chipFeedAll;
    private Chip chipFeedStudents;
    private Chip chipFeedProjects;
    private View homeHealthyCard;
    private View homePartialWarningCard;

    private View loading;
    private View empty;
    private View error;
    private TextView errorMessage;
    private MaterialButton manageClasses;

    private int loadGeneration;
    private boolean bootstrapReady;
    private boolean loadedOnce;
    private boolean skipNextResumeReload;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        homeScrollView = view.findViewById(R.id.home_scroll_view);
        classList = view.findViewById(R.id.class_list);
        interventionList = view.findViewById(R.id.intervention_list);
        feedFilterGroup = view.findViewById(R.id.feed_filter_group);
        chipFeedAll = view.findViewById(R.id.chip_feed_all);
        chipFeedStudents = view.findViewById(R.id.chip_feed_students);
        chipFeedProjects = view.findViewById(R.id.chip_feed_projects);
        homeHealthyCard = view.findViewById(R.id.home_healthy_card);
        homePartialWarningCard = view.findViewById(R.id.home_partial_warning_card);

        loading = view.findViewById(R.id.home_loading);
        empty = view.findViewById(R.id.home_empty);
        error = view.findViewById(R.id.home_error);
        errorMessage = view.findViewById(R.id.home_error_message);
        manageClasses = view.findViewById(R.id.manage_classes);

        classAdapter = new ClassCardAdapter(this::openClass);
        classList.setLayoutManager(new LinearLayoutManager(requireContext()));
        classList.setAdapter(classAdapter);

        interventionAdapter = new DashboardInterventionAdapter(new DashboardInterventionAdapter.OnInterventionClickListener() {
            @Override
            public void onStudentClicked(@NonNull String studentId, @NonNull String classId, @NonNull String weekId) {
                openStudent(studentId, classId, weekId);
            }

            @Override
            public void onProjectClicked(@NonNull String projectId) {
                openProject(projectId);
            }
        });
        interventionList.setLayoutManager(new LinearLayoutManager(requireContext()));
        interventionList.setAdapter(interventionAdapter);

        feedFilterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty() || interventionAdapter == null) {
                return;
            }
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_feed_all) {
                interventionAdapter.setFilter(DashboardInterventionAdapter.Filter.ALL);
            } else if (checkedId == R.id.chip_feed_students) {
                interventionAdapter.setFilter(DashboardInterventionAdapter.Filter.STUDENTS);
            } else if (checkedId == R.id.chip_feed_projects) {
                interventionAdapter.setFilter(DashboardInterventionAdapter.Filter.PROJECTS);
            }
        });

        View.OnClickListener manageListener = ignored -> openManageClasses();
        manageClasses.setOnClickListener(manageListener);
        view.findViewById(R.id.empty_manage_classes).setOnClickListener(manageListener);
        view.findViewById(R.id.home_retry).setOnClickListener(ignored -> retryCurrentFailure());

        MainActivity activity = (MainActivity) requireActivity();
        onCurriculumBootstrapState(activity.getCurriculumState(),
                activity.getCurriculumError());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (skipNextResumeReload) {
            skipNextResumeReload = false;
            return;
        }
        if (bootstrapReady && loadedOnce) {
            loadDashboard();
        }
    }

    @Override
    public void onDestroyView() {
        loadGeneration++;
        classAdapter = null;
        interventionAdapter = null;
        homeScrollView = null;
        classList = null;
        interventionList = null;
        feedFilterGroup = null;
        chipFeedAll = null;
        chipFeedStudents = null;
        chipFeedProjects = null;
        homeHealthyCard = null;
        homePartialWarningCard = null;
        loading = null;
        empty = null;
        error = null;
        errorMessage = null;
        manageClasses = null;
        super.onDestroyView();
    }

    public void onCurriculumBootstrapState(MainActivity.CurriculumState state,
                                           @Nullable Exception failure) {
        if (getView() == null) {
            return;
        }
        if (state == MainActivity.CurriculumState.READY) {
            bootstrapReady = true;
            if (!isResumed()) {
                skipNextResumeReload = true;
            }
            loadDashboard();
        } else if (state == MainActivity.CurriculumState.ERROR) {
            bootstrapReady = false;
            showError(getString(R.string.bootstrap_failed) + "\n"
                    + FirestoreErrorMessages.forException(failure));
        } else {
            bootstrapReady = false;
            showLoading();
        }
    }

    private void retryCurrentFailure() {
        MainActivity activity = (MainActivity) requireActivity();
        if (!bootstrapReady) {
            activity.retryCurriculumBootstrap();
        } else {
            loadDashboard();
        }
    }

    private void loadDashboard() {
        if (!bootstrapReady || getView() == null) {
            return;
        }
        int generation = ++loadGeneration;
        loadedOnce = true;
        showLoading();

        classRepository.loadActiveClasses()
                .addOnSuccessListener(classes -> {
                    if (!isCurrent(generation)) {
                        return;
                    }
                    if (classes.isEmpty()) {
                        classAdapter.submitList(Collections.emptyList());
                        interventionAdapter.submitList(Collections.emptyList());
                        showEmpty();
                        return;
                    }
                    loadClassDataAndProjects(classes, generation);
                })
                .addOnFailureListener(failure -> {
                    if (isCurrent(generation)) {
                        showError(FirestoreErrorMessages.forException(failure));
                    }
                });
    }

    private void loadClassDataAndProjects(List<ClassOffering> classes, int generation) {
        Set<String> trackIds = new HashSet<>();
        for (ClassOffering c : classes) {
            if (c.getTrackId() != null) {
                trackIds.add(c.getTrackId());
            }
        }

        Map<String, Task<Track>> trackTasks = new HashMap<>();
        for (String trackId : trackIds) {
            trackTasks.put(trackId, trackRepository.loadTrack(trackId));
        }

        Map<String, Task<CurriculumWeek>> weekTasks = new HashMap<>();
        Map<String, Task<List<Enrollment>>> enrollmentTasks = new HashMap<>();
        Map<String, Task<List<StudentProgress>>> progressTasks = new HashMap<>();

        for (ClassOffering c : classes) {
            String classId = c.getClassId();
            String weekId = CurriculumCatalog.weekId(c.getCurrentWeek());
            weekTasks.put(classId, trackRepository.loadWeek(c.getTrackId(), weekId));
            enrollmentTasks.put(classId, enrollmentRepository.loadActiveForClass(classId));
            progressTasks.put(classId, studentProgressRepository.loadForClassWeekNumber(classId, c.getCurrentWeek()));
        }

        Task<List<Project>> projectTask = projectRepository.loadActiveProjects();

        List<Task<?>> stageOneTasks = new ArrayList<>();
        stageOneTasks.addAll(trackTasks.values());
        stageOneTasks.addAll(weekTasks.values());
        stageOneTasks.addAll(enrollmentTasks.values());
        stageOneTasks.addAll(progressTasks.values());
        stageOneTasks.add(projectTask);

        Tasks.whenAllComplete(stageOneTasks).addOnCompleteListener(stageOneComplete -> {
            if (!isCurrent(generation)) {
                return;
            }

            Map<String, Track> tracksByTrackId = new HashMap<>();
            for (Map.Entry<String, Task<Track>> entry : trackTasks.entrySet()) {
                if (entry.getValue().isSuccessful()) {
                    tracksByTrackId.put(entry.getKey(), entry.getValue().getResult());
                }
            }

            Map<String, CurriculumWeek> currentWeeksByClassId = new HashMap<>();
            Set<String> classesWithReadFailures = new HashSet<>();

            for (ClassOffering c : classes) {
                String classId = c.getClassId();
                Task<CurriculumWeek> weekTask = weekTasks.get(classId);
                if (weekTask != null && weekTask.isSuccessful()) {
                    currentWeeksByClassId.put(classId, weekTask.getResult());
                } else {
                    classesWithReadFailures.add(classId);
                }

                if (!tracksByTrackId.containsKey(c.getTrackId())) {
                    classesWithReadFailures.add(classId);
                }
            }

            Map<String, List<Enrollment>> activeEnrollmentsByClassId = new HashMap<>();
            for (ClassOffering c : classes) {
                String classId = c.getClassId();
                Task<List<Enrollment>> eTask = enrollmentTasks.get(classId);
                if (eTask != null && eTask.isSuccessful()) {
                    activeEnrollmentsByClassId.put(classId, eTask.getResult());
                } else {
                    classesWithReadFailures.add(classId);
                }
            }

            Map<String, List<StudentProgress>> currentProgressByClassId = new HashMap<>();
            for (ClassOffering c : classes) {
                String classId = c.getClassId();
                Task<List<StudentProgress>> pTask = progressTasks.get(classId);
                if (pTask != null && pTask.isSuccessful()) {
                    currentProgressByClassId.put(classId, pTask.getResult());
                } else {
                    classesWithReadFailures.add(classId);
                }
            }

            Set<String> activeClassIds = new HashSet<>();
            for (ClassOffering c : classes) {
                activeClassIds.add(c.getClassId());
            }

            boolean projectReadFailed = !projectTask.isSuccessful() || projectTask.getResult() == null;
            List<Project> activeProjects = !projectReadFailed ? projectTask.getResult() : Collections.emptyList();
            if (projectReadFailed) {
                classesWithReadFailures.addAll(activeClassIds);
            }

            // Stage 2: Identify entities needing attention details
            Set<String> neededStudentIds = new HashSet<>();
            for (ClassOffering c : classes) {
                String classId = c.getClassId();
                List<Enrollment> enrollments = activeEnrollmentsByClassId.get(classId);
                List<StudentProgress> progresses = currentProgressByClassId.get(classId);
                if (enrollments == null || progresses == null) {
                    continue;
                }

                Set<String> activeStudentIds = new HashSet<>();
                for (Enrollment e : enrollments) {
                    if (e != null && Enrollment.STATUS_ACTIVE.equals(e.getStatus())
                            && classId.equals(e.getClassId())) {
                        activeStudentIds.add(e.getStudentId());
                    }
                }

                for (StudentProgress sp : progresses) {
                    if (sp == null) continue;
                    String status = sp.getOverallStatus();
                    if ((StudentProgress.STATUS_BLOCKED.equals(status)
                            || StudentProgress.STATUS_NEEDS_ATTENTION.equals(status))
                            && activeStudentIds.contains(sp.getStudentId())) {
                        neededStudentIds.add(sp.getStudentId());
                    }
                }
            }

            List<Project> blockedProjects = new ArrayList<>();
            for (Project p : activeProjects) {
                if (p != null && p.isActive() && activeClassIds.contains(p.getClassId())
                        && StudentProgress.STATUS_BLOCKED.equals(p.getOverallStatus())) {
                    blockedProjects.add(p);
                }
            }

            Map<String, Task<Student>> studentTasks = new HashMap<>();
            for (String studentId : neededStudentIds) {
                studentTasks.put(studentId, studentRepository.loadStudent(studentId));
            }

            Map<String, Task<ProjectProgress>> projectProgressTasks = new HashMap<>();
            for (Project p : blockedProjects) {
                String weekId = CurriculumCatalog.weekId(p.getCurrentWeek());
                projectProgressTasks.put(p.getProjectId(),
                        projectProgressRepository.loadOne(p.getProjectId(), weekId));
            }

            List<Task<?>> stageTwoTasks = new ArrayList<>();
            stageTwoTasks.addAll(studentTasks.values());
            stageTwoTasks.addAll(projectProgressTasks.values());

            if (stageTwoTasks.isEmpty()) {
                finishDerivationAndRender(classes, tracksByTrackId, currentWeeksByClassId,
                        activeEnrollmentsByClassId, currentProgressByClassId,
                        Collections.emptyMap(), activeProjects, Collections.emptyMap(),
                        classesWithReadFailures, projectReadFailed, generation);
                return;
            }

            Tasks.whenAllComplete(stageTwoTasks).addOnCompleteListener(stageTwoComplete -> {
                if (!isCurrent(generation)) {
                    return;
                }

                Map<String, Student> studentsById = new HashMap<>();
                for (Map.Entry<String, Task<Student>> entry : studentTasks.entrySet()) {
                    if (entry.getValue().isSuccessful() && entry.getValue().getResult() != null) {
                        studentsById.put(entry.getKey(), entry.getValue().getResult());
                    } else {
                        // Student detail read failed! Mark affected class as partially unavailable
                        String failedStudentId = entry.getKey();
                        for (ClassOffering c : classes) {
                            List<Enrollment> enrs = activeEnrollmentsByClassId.get(c.getClassId());
                            if (enrs != null) {
                                for (Enrollment e : enrs) {
                                    if (failedStudentId.equals(e.getStudentId())) {
                                        classesWithReadFailures.add(c.getClassId());
                                    }
                                }
                            }
                        }
                    }
                }

                Map<String, ProjectProgress> projectProgressByProjectId = new HashMap<>();
                for (Map.Entry<String, Task<ProjectProgress>> entry : projectProgressTasks.entrySet()) {
                    if (entry.getValue().isSuccessful() && entry.getValue().getResult() != null) {
                        projectProgressByProjectId.put(entry.getKey(), entry.getValue().getResult());
                    }
                }

                finishDerivationAndRender(classes, tracksByTrackId, currentWeeksByClassId,
                        activeEnrollmentsByClassId, currentProgressByClassId,
                        studentsById, activeProjects, projectProgressByProjectId,
                        classesWithReadFailures, projectReadFailed, generation);
            });
        });
    }

    private void finishDerivationAndRender(
            List<ClassOffering> classes,
            Map<String, Track> tracksByTrackId,
            Map<String, CurriculumWeek> currentWeeksByClassId,
            Map<String, List<Enrollment>> activeEnrollmentsByClassId,
            Map<String, List<StudentProgress>> currentProgressByClassId,
            Map<String, Student> studentsById,
            List<Project> activeProjects,
            Map<String, ProjectProgress> projectProgressByProjectId,
            Set<String> classesWithReadFailures,
            boolean projectReadFailed,
            int generation) {

        if (!isCurrent(generation)) {
            return;
        }

        DashboardDerivationHelper.DerivationResult result = DashboardDerivationHelper.derive(
                classes,
                tracksByTrackId,
                currentWeeksByClassId,
                activeEnrollmentsByClassId,
                currentProgressByClassId,
                studentsById,
                activeProjects,
                projectProgressByProjectId,
                classesWithReadFailures,
                projectReadFailed
        );

        if (result.getClassSummaries().isEmpty() && !classes.isEmpty()) {
            showError(getString(R.string.dashboard_partial_warning));
            return;
        }

        classAdapter.submitList(result.getClassSummaries());
        interventionAdapter.submitList(result.getInterventionItems());

        // Update Filter Chip badges
        chipFeedAll.setText(getString(R.string.filter_feed_all, interventionAdapter.getAllCount()));
        chipFeedStudents.setText(getString(R.string.filter_feed_students, interventionAdapter.getStudentCount()));
        chipFeedProjects.setText(getString(R.string.filter_feed_projects, interventionAdapter.getProjectCount()));

        // Apply current filter selection
        int checkedId = feedFilterGroup.getCheckedChipId();
        if (checkedId == R.id.chip_feed_students) {
            interventionAdapter.setFilter(DashboardInterventionAdapter.Filter.STUDENTS);
        } else if (checkedId == R.id.chip_feed_projects) {
            interventionAdapter.setFilter(DashboardInterventionAdapter.Filter.PROJECTS);
        } else {
            feedFilterGroup.check(R.id.chip_feed_all);
            interventionAdapter.setFilter(DashboardInterventionAdapter.Filter.ALL);
        }

        boolean isPartial = result.hasPartialFailure() || projectReadFailed;
        if (homePartialWarningCard != null) {
            homePartialWarningCard.setVisibility(isPartial ? View.VISIBLE : View.GONE);
        }

        // Healthy state card visibility
        if (result.getInterventionItems().isEmpty()) {
            if (isPartial) {
                // Do NOT show healthy state as fully verified if partial data exists
                homeHealthyCard.setVisibility(View.GONE);
            } else {
                homeHealthyCard.setVisibility(View.VISIBLE);
            }
            interventionList.setVisibility(View.GONE);
        } else {
            homeHealthyCard.setVisibility(View.GONE);
            interventionList.setVisibility(View.VISIBLE);
        }

        showContent();
    }

    private void openManageClasses() {
        startActivity(new Intent(requireContext(), ManageClassesActivity.class));
    }

    private void openClass(String classId) {
        Intent intent = new Intent(requireContext(), ClassDetailActivity.class);
        intent.putExtra(ClassDetailActivity.EXTRA_CLASS_ID, classId);
        startActivity(intent);
    }

    private void openStudent(String studentId, String classId, String weekId) {
        Intent intent = new Intent(requireContext(), StudentDetailActivity.class);
        intent.putExtra(StudentDetailActivity.EXTRA_STUDENT_ID, studentId);
        intent.putExtra(StudentDetailActivity.EXTRA_CLASS_ID, classId);
        intent.putExtra(StudentDetailActivity.EXTRA_WEEK_ID, weekId);
        startActivity(intent);
    }

    private void openProject(String projectId) {
        Intent intent = new Intent(requireContext(), ProjectDetailActivity.class);
        intent.putExtra(ProjectDetailActivity.EXTRA_PROJECT_ID, projectId);
        startActivity(intent);
    }

    private boolean isCurrent(int generation) {
        return isAdded() && getView() != null && generation == loadGeneration;
    }

    private void showLoading() {
        if (loading == null) {
            return;
        }
        loading.setVisibility(View.VISIBLE);
        homeScrollView.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        manageClasses.setEnabled(bootstrapReady);
    }

    private void showContent() {
        loading.setVisibility(View.GONE);
        homeScrollView.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        manageClasses.setEnabled(true);
    }

    private void showEmpty() {
        loading.setVisibility(View.GONE);
        homeScrollView.setVisibility(View.GONE);
        empty.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        manageClasses.setEnabled(true);
    }

    private void showError(String message) {
        if (errorMessage == null) {
            return;
        }
        loading.setVisibility(View.GONE);
        homeScrollView.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE);
        errorMessage.setText(message);
        manageClasses.setEnabled(bootstrapReady);
    }
}
