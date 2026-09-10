package th.ac.vu.classflow.ui.model;

import th.ac.vu.classflow.data.model.StudentProgress;

public final class ProgressPresentation {
    public static final String NOT_RECORDED = "NOT_RECORDED";
    public static final String FILTER_ALL = "ALL";

    private ProgressPresentation() { }

    public static String label(String status) {
        if (NOT_RECORDED.equals(status)) return "Not recorded";
        if (StudentProgress.STATUS_NOT_STARTED.equals(status)) return "Not Started";
        if (StudentProgress.STATUS_ON_TRACK.equals(status)) return "On Track";
        if (StudentProgress.STATUS_NEEDS_ATTENTION.equals(status)) return "Needs Attention";
        if (StudentProgress.STATUS_BLOCKED.equals(status)) return "Blocked";
        if (StudentProgress.STATUS_COMPLETED.equals(status)) return "Completed";
        return status;
    }
}
