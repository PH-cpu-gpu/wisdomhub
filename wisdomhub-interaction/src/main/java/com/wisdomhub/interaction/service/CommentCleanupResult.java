package com.wisdomhub.interaction.service;

public class CommentCleanupResult {

    private final int scanned;

    private final int deleted;

    private final int failed;

    public CommentCleanupResult(int scanned, int deleted, int failed) {
        this.scanned = scanned;
        this.deleted = deleted;
        this.failed = failed;
    }

    public boolean hasFailure() {
        return failed > 0;
    }

    public String summary() {
        return "scanned=" + scanned + ", deleted=" + deleted + ", failed=" + failed;
    }
}
