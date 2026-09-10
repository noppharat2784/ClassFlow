package th.ac.vu.classflow.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Track;

public final class TrackRepository {

    private final FirebaseFirestore firestore;

    public TrackRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public Task<List<Track>> loadActiveTracks() {
        return firestore.collection("tracks")
                .whereEqualTo("active", true)
                .get()
                .continueWith(task -> {
                    List<Track> tracks = new ArrayList<>();
                    for (DocumentSnapshot snapshot : task.getResult().getDocuments()) {
                        Track track = snapshot.toObject(Track.class);
                        if (track == null) {
                            throw new DataValidationException("The curriculum Track data is invalid.");
                        }
                        track.setTrackId(snapshot.getId());
                        validateTrack(track);
                        tracks.add(track);
                    }
                    tracks.sort(Comparator.comparing(Track::getCode,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
                    return tracks;
                });
    }

    public Task<Track> loadTrack(String trackId) {
        return firestore.collection("tracks").document(trackId).get()
                .continueWith(task -> {
                    DocumentSnapshot snapshot = task.getResult();
                    if (!snapshot.exists()) {
                        throw new DataNotFoundException("The curriculum Track was not found.");
                    }
                    Track track = snapshot.toObject(Track.class);
                    if (track == null) {
                        throw new DataValidationException("The curriculum Track data is invalid.");
                    }
                    track.setTrackId(snapshot.getId());
                    validateTrack(track);
                    return track;
                });
    }

    public Task<List<CurriculumWeek>> loadWeeks(String trackId) {
        return firestore.collection("tracks").document(trackId).collection("weeks")
                .orderBy("weekNumber")
                .get()
                .continueWith(task -> {
                    List<CurriculumWeek> weeks = new ArrayList<>();
                    for (DocumentSnapshot snapshot : task.getResult().getDocuments()) {
                        CurriculumWeek week = snapshot.toObject(CurriculumWeek.class);
                        if (week == null) {
                            throw new DataValidationException("The curriculum Week data is invalid.");
                        }
                        week.setWeekId(snapshot.getId());
                        validateWeek(week);
                        weeks.add(week);
                    }
                    weeks.sort(Comparator.comparingInt(CurriculumWeek::getWeekNumber));
                    return weeks;
                });
    }

    public Task<CurriculumWeek> loadWeek(String trackId, String weekId) {
        return firestore.collection("tracks").document(trackId).collection("weeks")
                .document(weekId)
                .get()
                .continueWith(task -> {
                    DocumentSnapshot snapshot = task.getResult();
                    if (!snapshot.exists()) {
                        throw new DataNotFoundException("The curriculum Week was not found.");
                    }
                    CurriculumWeek week = snapshot.toObject(CurriculumWeek.class);
                    if (week == null) {
                        throw new DataValidationException("The curriculum Week data is invalid.");
                    }
                    week.setWeekId(snapshot.getId());
                    validateWeek(week);
                    return week;
                });
    }

    private void validateTrack(Track track) {
        if (isBlank(track.getTrackId()) || isBlank(track.getCode())
                || isBlank(track.getName()) || isBlank(track.getDescription())
                || track.getTotalWeeks() < 1) {
            throw new DataValidationException("The curriculum Track data is invalid.");
        }
    }

    private void validateWeek(CurriculumWeek week) {
        String expectedId = String.format(java.util.Locale.US, "W%02d", week.getWeekNumber());
        if (week.getWeekNumber() < 1 || !expectedId.equals(week.getWeekId())
                || isBlank(week.getTitle()) || isBlank(week.getSummary())
                || isBlank(week.getPhase()) || isBlank(week.getProgressType())) {
            throw new DataValidationException("The curriculum Week data is invalid.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
