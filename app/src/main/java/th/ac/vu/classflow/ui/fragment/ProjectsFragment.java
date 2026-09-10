package th.ac.vu.classflow.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th.ac.vu.classflow.R;
import th.ac.vu.classflow.data.firebase.FirestoreErrorMessages;
import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.ProjectProgress;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.Track;
import th.ac.vu.classflow.data.repository.ClassRepository;
import th.ac.vu.classflow.data.repository.ProjectProgressRepository;
import th.ac.vu.classflow.data.repository.ProjectRepository;
import th.ac.vu.classflow.data.repository.StudentRepository;
import th.ac.vu.classflow.data.repository.TrackRepository;
import th.ac.vu.classflow.ui.activity.ProjectDetailActivity;
import th.ac.vu.classflow.ui.adapter.ProjectAdapter;
import th.ac.vu.classflow.ui.dialog.ProjectEditorDialogFragment;
import th.ac.vu.classflow.ui.model.ProjectCardItem;

public final class ProjectsFragment extends Fragment {

    private static final String TRACK_ALL = "ALL";
    private static final String STATUS_ALL = "ALL";

    private final ProjectRepository projectRepository = new ProjectRepository();
    private final ProjectProgressRepository projectProgressRepository = new ProjectProgressRepository();
    private final ClassRepository classRepository = new ClassRepository();
    private final TrackRepository trackRepository = new TrackRepository();
    private final StudentRepository studentRepository = new StudentRepository();

    private final List<ProjectCardItem> allItems = new ArrayList<>();

    private View root;
    private RecyclerView recyclerView;
    private ProjectAdapter adapter;
    private View loading;
    private TextView emptyMessage;
    private View errorContainer;
    private TextView errorMessage;
    private TextInputEditText searchInput;

    private boolean activeFilter = true;
    private String trackFilter = TRACK_ALL;
    private String statusFilter = STATUS_ALL;
    private String searchQuery = "";
    private int loadGeneration;
    private boolean resumedOnce;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_projects, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        bindViews(view);
        configureList();
        configureActions();

        getParentFragmentManager().setFragmentResultListener(
                ProjectEditorDialogFragment.RESULT_PROJECT_CHANGED, this,
                (key, result) -> loadProjects());

        loadProjects();
    }

    @Override
    public void onDestroyView() {
        loadGeneration++;
        resumedOnce = false;
        root = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (resumedOnce && root != null) {
            loadProjects();
        }
        resumedOnce = true;
    }

    private void bindViews(View view) {
        recyclerView = view.findViewById(R.id.projects_recycler);
        loading = view.findViewById(R.id.projects_loading);
        emptyMessage = view.findViewById(R.id.projects_empty_message);
        errorContainer = view.findViewById(R.id.projects_error_container);
        errorMessage = view.findViewById(R.id.projects_error_message);
        searchInput = view.findViewById(R.id.project_search);
    }

    private void configureList() {
        adapter = new ProjectAdapter(new ProjectAdapter.Actions() {
            @Override
            public void onOpen(ProjectCardItem item) {
                openProjectDetail(item);
            }

            @Override
            public void onEdit(ProjectCardItem item) {
                openProjectEditor(item);
            }

            @Override
            public void onArchive(ProjectCardItem item) {
                confirmArchiveProject(item);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void configureActions() {
        root.findViewById(R.id.btn_add_project).setOnClickListener(v ->
                ProjectEditorDialogFragment.newCreate(null)
                        .show(getParentFragmentManager(), "project_create"));

        root.findViewById(R.id.btn_projects_retry).setOnClickListener(v -> loadProjects());

        root.findViewById(R.id.chip_filter_active).setOnClickListener(v -> {
            if (!activeFilter) {
                activeFilter = true;
                loadProjects();
            }
        });

        root.findViewById(R.id.chip_filter_archived).setOnClickListener(v -> {
            if (activeFilter) {
                activeFilter = false;
                loadProjects();
            }
        });

        root.findViewById(R.id.chip_track_all).setOnClickListener(v -> setTrackFilter(TRACK_ALL));
        root.findViewById(R.id.chip_track_ss1).setOnClickListener(v -> setTrackFilter("ss1"));
        root.findViewById(R.id.chip_track_ss2).setOnClickListener(v -> setTrackFilter("ss2"));

        bindStatusFilter(R.id.chip_status_all, STATUS_ALL);
        bindStatusFilter(R.id.chip_status_not_started, Project.STATUS_NOT_STARTED);
        bindStatusFilter(R.id.chip_status_on_track, Project.STATUS_ON_TRACK);
        bindStatusFilter(R.id.chip_status_needs_attention, Project.STATUS_NEEDS_ATTENTION);
        bindStatusFilter(R.id.chip_status_blocked, Project.STATUS_BLOCKED);
        bindStatusFilter(R.id.chip_status_completed, Project.STATUS_COMPLETED);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private void bindStatusFilter(int viewId, String status) {
        root.findViewById(viewId).setOnClickListener(v -> {
            statusFilter = status;
            applyFilters();
        });
    }

    private void setTrackFilter(String track) {
        trackFilter = track;
        applyFilters();
    }

    private void loadProjects() {
        int request = ++loadGeneration;
        showLoading();

        Task<List<Project>> projectTask = activeFilter
                ? projectRepository.loadActiveProjects()
                : projectRepository.loadAllProjects();

        projectTask.addOnSuccessListener(projects -> {
            if (!usable(request)) return;

            List<Project> targetProjects = new ArrayList<>();
            for (Project p : projects) {
                if (activeFilter) {
                    if (p.isActive()) targetProjects.add(p);
                } else {
                    if (!p.isActive()) targetProjects.add(p);
                }
            }

            if (targetProjects.isEmpty()) {
                allItems.clear();
                applyFilters();
                return;
            }

            List<Task<ProjectCardItem>> tasks = new ArrayList<>();
            for (Project p : targetProjects) {
                tasks.add(resolveProjectCard(p));
            }

            Tasks.whenAllComplete(tasks).addOnCompleteListener(ignored -> {
                if (!usable(request)) return;
                List<ProjectCardItem> resolved = new ArrayList<>();
                for (Task<ProjectCardItem> t : tasks) {
                    if (t.isSuccessful() && t.getResult() != null) {
                        resolved.add(t.getResult());
                    }
                }
                allItems.clear();
                allItems.addAll(resolved);
                applyFilters();
            });
        }).addOnFailureListener(e -> {
            if (!usable(request)) return;
            showError(e);
        });
    }

    private Task<ProjectCardItem> resolveProjectCard(Project project) {
        Task<ClassOffering> classTask = classRepository.loadClass(project.getClassId());
        Task<Track> trackTask = trackRepository.loadTrack(project.getTrackId());

        List<Task<Student>> memberTasks = new ArrayList<>();
        for (String mid : project.getMemberIds()) {
            memberTasks.add(studentRepository.loadStudent(mid));
        }

        Task<ProjectProgress> blockerProgressTask;
        if (Project.STATUS_BLOCKED.equals(project.getOverallStatus())) {
            String weekId = String.format(Locale.US, "W%02d", project.getCurrentWeek());
            blockerProgressTask = projectProgressRepository.loadOne(project.getProjectId(), weekId);
        } else {
            blockerProgressTask = Tasks.forResult(null);
        }

        return Tasks.whenAllComplete(classTask, trackTask, blockerProgressTask, Tasks.whenAllComplete(memberTasks))
                .continueWith(t -> {
                    ClassOffering classOffering = classTask.isSuccessful() ? classTask.getResult() : null;
                    Track track = trackTask.isSuccessful() ? trackTask.getResult() : null;
                    List<Student> members = new ArrayList<>();
                    for (Task<Student> st : memberTasks) {
                        if (st.isSuccessful() && st.getResult() != null) {
                            members.add(st.getResult());
                        }
                    }
                    String blocker = null;
                    if (blockerProgressTask.isSuccessful() && blockerProgressTask.getResult() != null) {
                        blocker = blockerProgressTask.getResult().getBlocker();
                    }
                    return new ProjectCardItem(project, classOffering, track, members, blocker);
                });
    }

    private void applyFilters() {
        List<ProjectCardItem> filtered = new ArrayList<>();
        for (ProjectCardItem item : allItems) {
            Project p = item.getProject();

            if (!TRACK_ALL.equals(trackFilter) && !trackFilter.equalsIgnoreCase(p.getTrackId())) {
                continue;
            }

            if (!STATUS_ALL.equals(statusFilter) && !statusFilter.equals(p.getOverallStatus())) {
                continue;
            }

            if (!searchQuery.isEmpty()) {
                boolean matchTitle = p.getTitle() != null && p.getTitle().toLowerCase(Locale.US).contains(searchQuery);
                boolean matchTeam = p.getTeamName() != null && p.getTeamName().toLowerCase(Locale.US).contains(searchQuery);
                if (!matchTitle && !matchTeam) {
                    continue;
                }
            }

            filtered.add(item);
        }

        adapter.submitList(filtered);
        hideLoading();

        boolean noResults = filtered.isEmpty();
        recyclerView.setVisibility(noResults ? View.GONE : View.VISIBLE);
        emptyMessage.setVisibility(noResults ? View.VISIBLE : View.GONE);

        if (noResults) {
            boolean hasFilters = !searchQuery.isEmpty() || !TRACK_ALL.equals(trackFilter) || !STATUS_ALL.equals(statusFilter);
            if (hasFilters && !allItems.isEmpty()) {
                emptyMessage.setText(R.string.no_matching_projects);
            } else {
                emptyMessage.setText(activeFilter ? R.string.no_active_projects : R.string.no_archived_projects);
            }
        }
    }

    private void openProjectDetail(ProjectCardItem item) {
        Intent intent = new Intent(requireContext(), ProjectDetailActivity.class);
        intent.putExtra("project_id", item.getProject().getProjectId());
        startActivity(intent);
    }

    private void openProjectEditor(ProjectCardItem item) {
        ProjectEditorDialogFragment.newEdit(item.getProject().getProjectId())
                .show(getParentFragmentManager(), "project_edit");
    }

    private void confirmArchiveProject(ProjectCardItem item) {
        Project project = item.getProject();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.archive_project_title)
                .setMessage(R.string.archive_project_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.archive_project, (d, w) -> {
                    showLoading();
                    projectRepository.archiveProject(project.getProjectId())
                            .addOnSuccessListener(unused -> {
                                if (root != null) {
                                    Snackbar.make(root, R.string.project_archived, Snackbar.LENGTH_SHORT).show();
                                }
                                loadProjects();
                            })
                            .addOnFailureListener(this::showError);
                })
                .show();
    }

    private void showLoading() {
        loading.setVisibility(View.VISIBLE);
        emptyMessage.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loading.setVisibility(View.INVISIBLE);
        errorContainer.setVisibility(View.GONE);
    }

    private void showError(Exception e) {
        loading.setVisibility(View.INVISIBLE);
        emptyMessage.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
        errorMessage.setText(FirestoreErrorMessages.forException(e));
    }

    private boolean usable(int request) {
        return root != null && isAdded() && request == loadGeneration;
    }
}
