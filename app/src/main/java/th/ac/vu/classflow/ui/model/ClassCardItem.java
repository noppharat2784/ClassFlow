package th.ac.vu.classflow.ui.model;

import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Track;

public final class ClassCardItem {
    private final ClassOffering classOffering;
    private final Track track;
    private final CurriculumWeek currentWeek;

    public ClassCardItem(ClassOffering classOffering, Track track, CurriculumWeek currentWeek) {
        this.classOffering = classOffering;
        this.track = track;
        this.currentWeek = currentWeek;
    }

    public ClassOffering getClassOffering() {
        return classOffering;
    }

    public Track getTrack() {
        return track;
    }

    public CurriculumWeek getCurrentWeek() {
        return currentWeek;
    }
}
