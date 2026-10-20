package com.wisdomhub.search.service.reconcile;

import lombok.Data;

/** 对账结果，只用于任务日志与验收。 */
@Data
public class ReconcileResult {

    private int scanned;

    private int mismatched;

    private int republished;

    private int orphans;

    public String summary() {
        return "scanned=" + scanned + ", mismatched=" + mismatched
                + ", republished=" + republished + ", orphans=" + orphans;
    }
}
