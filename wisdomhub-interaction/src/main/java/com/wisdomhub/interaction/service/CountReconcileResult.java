package com.wisdomhub.interaction.service;

import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CountReconcileResult {

    private final Map<Integer, Step> steps = new LinkedHashMap<>();

    public void add(int targetType, int scanned, int fixedRows, int fixedColumns,
                    int inserted, int deleted, int failed) {
        steps.put(targetType, new Step(scanned, fixedRows, fixedColumns, inserted, deleted, failed));
    }

    public boolean hasFailure() {
        return steps.values().stream().anyMatch(step -> step.failed > 0);
    }

    public String summary() {
        if (steps.isEmpty()) {
            return "no steps";
        }
        return steps.entrySet().stream()
                .map(e -> ContentTargetTypeConstants.nameOf(e.getKey())
                        + "(scanned=" + e.getValue().scanned
                        + ", fixedRows=" + e.getValue().fixedRows
                        + ", fixedColumns=" + e.getValue().fixedColumns
                        + ", inserted=" + e.getValue().inserted
                        + ", deleted=" + e.getValue().deleted
                        + ", failed=" + e.getValue().failed + ")")
                .collect(Collectors.joining(", "));
    }

    private record Step(int scanned, int fixedRows, int fixedColumns,
                        int inserted, int deleted, int failed) {
    }
}
