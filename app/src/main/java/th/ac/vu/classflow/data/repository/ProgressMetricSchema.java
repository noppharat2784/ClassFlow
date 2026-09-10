package th.ac.vu.classflow.data.repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProgressMetricSchema {
    private ProgressMetricSchema() { }

    public static List<String> resolve(String trackId, String progressType, int weekNumber) {
        if ("ss1".equals(trackId)) {
            if (weekNumber >= 1 && weekNumber <= 8 && "LEARNING".equals(progressType)) {
                return Arrays.asList("concept", "practice");
            }
            if (weekNumber == 9 && "PROJECT".equals(progressType)) {
                return Arrays.asList("planning", "implementation", "testing");
            }
            if (weekNumber == 10 && "DEMO".equals(progressType)) {
                return Arrays.asList("demoReadiness", "codeExplanation", "communication");
            }
        }
        if ("ss2".equals(trackId)) {
            if (weekNumber >= 1 && weekNumber <= 8 && "INTEGRATION".equals(progressType)) {
                return Arrays.asList("software", "hardware", "integration");
            }
            if (weekNumber == 9 && "PROJECT".equals(progressType)) {
                return Arrays.asList("software", "hardware", "integration");
            }
            if (weekNumber == 10 && "DEMO".equals(progressType)) {
                return Arrays.asList("demoReadiness", "systemExplanation", "communication");
            }
        }
        throw new DataValidationException("This Track/Week has no valid Progress metric schema.");
    }

    public static List<String> metricValues() {
        return Collections.unmodifiableList(Arrays.asList(
                "NOT_STARTED", "IN_PROGRESS", "DONE", "NEEDS_PRACTICE", "GOOD"));
    }

    public static List<String> overallStatuses() {
        return Collections.unmodifiableList(Arrays.asList(
                "NOT_STARTED", "ON_TRACK", "NEEDS_ATTENTION", "BLOCKED", "COMPLETED"));
    }

    public static String label(String key) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char character = key.charAt(i);
            if (i > 0 && Character.isUpperCase(character)) value.append(' ');
            value.append(i == 0 ? Character.toUpperCase(character) : character);
        }
        return value.toString();
    }
}
