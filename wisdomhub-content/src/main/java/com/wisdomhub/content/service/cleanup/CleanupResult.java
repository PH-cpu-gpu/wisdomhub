package com.wisdomhub.content.service.cleanup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CleanupResult {

    private final Map<String, Step> steps = new LinkedHashMap<>();

    public void add(String step, int scanned, int deleted, int skipped, int failed) {
        steps.put(step, new Step(scanned, deleted, skipped, failed));
    }

    public boolean hasFailure() {
        return steps.values().stream().anyMatch(step -> step.failed > 0);
    }

    public String summary() {
        if (steps.isEmpty()) {
            return "no steps";
        }
        return steps.entrySet().stream()
                .map(e -> e.getKey() + "(scanned=" + e.getValue().scanned
                        + ", deleted=" + e.getValue().deleted
                        + ", skipped=" + e.getValue().skipped
                        + ", failed=" + e.getValue().failed + ")")
                .collect(Collectors.joining(", "));
    }

    private record Step(int scanned, int deleted, int skipped, int failed) {
    }
}
