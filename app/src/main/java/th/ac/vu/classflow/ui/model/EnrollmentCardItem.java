package th.ac.vu.classflow.ui.model;

import th.ac.vu.classflow.data.model.ClassOffering;
import th.ac.vu.classflow.data.model.Enrollment;
import th.ac.vu.classflow.data.model.Track;

public final class EnrollmentCardItem {
    private final Enrollment enrollment;
    private final ClassOffering classOffering;
    private final Track track;

    public EnrollmentCardItem(Enrollment enrollment, ClassOffering classOffering, Track track) {
        this.enrollment = enrollment;
        this.classOffering = classOffering;
        this.track = track;
    }

    public Enrollment getEnrollment() { return enrollment; }
    public ClassOffering getClassOffering() { return classOffering; }
    public Track getTrack() { return track; }
}
