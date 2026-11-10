package com.wisdomhub.video.domain;

/** 视频审核结果状态；PENDING 留给未来的异步审核实现。 */
public enum AuditStatus {

    PASSED,

    REJECTED,

    PENDING
}
