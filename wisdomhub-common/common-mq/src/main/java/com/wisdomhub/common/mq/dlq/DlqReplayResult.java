package com.wisdomhub.common.mq.dlq;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DlqReplayResult {

    private final Map<String, QueueStats> queues = new LinkedHashMap<>();

    public void replayed(String queue) {
        stats(queue).replayed++;
    }

    public void exhausted(String queue) {
        stats(queue).exhausted++;
    }

    public void invalid(String queue) {
        stats(queue).invalid++;
    }

    public void failed(String queue) {
        stats(queue).failed++;
    }

    public boolean hasFailure() {
        return queues.values().stream().anyMatch(s -> s.exhausted > 0 || s.invalid > 0 || s.failed > 0);
    }

    public int totalReplayed() {
        return queues.values().stream().mapToInt(s -> s.replayed).sum();
    }

    public String summary() {
        if (queues.isEmpty()) {
            return "no queues";
        }
        return queues.entrySet().stream()
                .map(e -> e.getKey() + "(replayed=" + e.getValue().replayed
                        + ", exhausted=" + e.getValue().exhausted
                        + ", invalid=" + e.getValue().invalid
                        + ", failed=" + e.getValue().failed + ")")
                .collect(Collectors.joining(", "));
    }

    private QueueStats stats(String queue) {
        return queues.computeIfAbsent(queue, k -> new QueueStats());
    }

    private static class QueueStats {
        private int replayed;
        private int exhausted;
        private int invalid;
        private int failed;
    }
}
