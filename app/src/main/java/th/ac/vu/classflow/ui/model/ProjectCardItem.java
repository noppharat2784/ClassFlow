package th.ac.vu.classflow.ui.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Project;
import th.ac.vu.classflow.data.model.Student;
import th.ac.vu.classflow.data.model.Track;

public final class ProjectCardItem {
    private final Project project;
    @Nullable private final ClassOffering classOffering;
    @Nullable private final Track track;
    private final List<Student> members;
    @Nullable private final String currentBlocker;

    public ProjectCardItem(Project project,
                           @Nullable ClassOffering classOffering,
                           @Nullable Track track,
                           List<Student> members,
                           @Nullable String currentBlocker) {
        this.project = project;
        this.classOffering = classOffering;
        this.track = track;
        this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
        this.currentBlocker = currentBlocker;
    }

    public Project getProject() {
        return project;
    }

    @Nullable
    public ClassOffering getClassOffering() {
        return classOffering;
    }

    @Nullable
    public Track getTrack() {
        return track;
    }

    public List<Student> getMembers() {
        return members;
    }

    @Nullable
    public String getCurrentBlocker() {
        return currentBlocker;
    }
}
