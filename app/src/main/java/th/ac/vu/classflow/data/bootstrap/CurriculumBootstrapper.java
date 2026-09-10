package th.ac.vu.classflow.data.bootstrap;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import th.ac.vu.classflow.data.model.CurriculumWeek;
import th.ac.vu.classflow.data.model.Track;

public final class CurriculumBootstrapper {

    private final FirebaseFirestore firestore;

    public CurriculumBootstrapper() {
        this(FirebaseFirestore.getInstance());
    }

    CurriculumBootstrapper(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public Task<Void> bootstrapMissingCurriculum() {
        List<SeedDocument> documents = seedDocuments();
        Task<Void> chain = Tasks.forResult(null);

        for (SeedDocument document : documents) {
            chain = chain.continueWithTask(previous -> {
                if (!previous.isSuccessful()) {
                    Exception failure = previous.getException();
                    return Tasks.forException(failure == null
                            ? new IllegalStateException("Curriculum bootstrap failed.")
                            : failure);
                }
                return createOnlyWhenMissing(document.reference, document.values);
            });
        }
        return chain;
    }

    private Task<Void> createOnlyWhenMissing(DocumentReference reference,
                                             Map<String, Object> values) {
        return firestore.runTransaction(transaction -> {
            if (!transaction.get(reference).exists()) {
                transaction.set(reference, values);
            }
            return null;
        });
    }

    private List<SeedDocument> seedDocuments() {
        List<SeedDocument> documents = new ArrayList<>();
        for (CurriculumCatalog.SeedTrack seedTrack : CurriculumCatalog.initialTracks()) {
            Track track = seedTrack.getTrack();
            DocumentReference trackReference = firestore.collection("tracks")
                    .document(track.getTrackId());
            documents.add(new SeedDocument(trackReference, track.toFirestoreMap()));

            for (CurriculumWeek week : seedTrack.getWeeks()) {
                DocumentReference weekReference = trackReference.collection("weeks")
                        .document(week.getWeekId());
                documents.add(new SeedDocument(weekReference, week.toFirestoreMap()));
            }
        }
        return documents;
    }

    private static final class SeedDocument {
        private final DocumentReference reference;
        private final Map<String, Object> values;

        private SeedDocument(DocumentReference reference, Map<String, Object> values) {
            this.reference = reference;
            this.values = values;
        }
    }
}
