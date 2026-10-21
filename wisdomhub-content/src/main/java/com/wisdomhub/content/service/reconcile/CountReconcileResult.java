package com.wisdomhub.content.service.reconcile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CountReconcileResult {

    private final Map<String, Step> steps = new LinkedHashMap<>();

    public void add(String entity, int scanned, int fixedRows, int fixedColumns, int failed) {
        steps.put(entity, new Step(scanned, fixedRows, fixedColumns, failed));
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
                        + ", fixedRows=" + e.getValue().fixedRows
                        + ", fixedColumns=" + e.getValue().fixedColumns
                        + ", failed=" + e.getValue().failed + ")")
                .collect(Collectors.joining(", "));
    }

    private record Step(int scanned, int fixedRows, int fixedColumns, int failed) {
    }
}
