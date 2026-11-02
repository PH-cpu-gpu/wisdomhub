package com.wisdomhub.content.service.promote;

/** 未收录候选升级结果，只用于任务日志与验收。 */
public class TagCandidatePromoteResult {

    private int scanned;

    private int promoted;

    private int rejectedSkipped;

    private int pendingSkipped;

    private int existsSkipped;

    private int invalidSkipped;

    private int failed;

    public void setScanned(int scanned) {
        this.scanned = scanned;
    }

    public void promoted() {
        this.promoted++;
    }

    public void rejectedSkipped() {
        this.rejectedSkipped++;
    }

    public void pendingSkipped() {
        this.pendingSkipped++;
    }

    public void existsSkipped() {
        this.existsSkipped++;
    }

    public void invalidSkipped() {
        this.invalidSkipped++;
    }

    public void failed() {
        this.failed++;
    }

    public boolean hasFailure() {
        return failed > 0;
    }

    public String summary() {
        return "scanned=" + scanned
                + ", promoted=" + promoted
                + ", rejectedSkipped=" + rejectedSkipped
                + ", pendingSkipped=" + pendingSkipped
                + ", existsSkipped=" + existsSkipped
                + ", invalidSkipped=" + invalidSkipped
                + ", failed=" + failed;
    }
}
