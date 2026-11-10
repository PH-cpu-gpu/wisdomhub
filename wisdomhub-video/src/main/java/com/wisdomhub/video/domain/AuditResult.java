package com.wisdomhub.video.domain;

/** 一次视频审核的结果。 */
public record AuditResult(AuditStatus status, String reason) {

    public static AuditResult passed() {
        return new AuditResult(AuditStatus.PASSED, null);
    }

    public static AuditResult rejected(String reason) {
        return new AuditResult(AuditStatus.REJECTED, reason);
    }

    public static AuditResult pending() {
        return new AuditResult(AuditStatus.PENDING, null);
    }
}
