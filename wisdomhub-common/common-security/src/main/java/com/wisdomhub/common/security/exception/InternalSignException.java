package com.wisdomhub.common.security.exception;

/**
 * 验签失败；reason 只进日志，不返回调用方。
 */
public class InternalSignException extends RuntimeException {

    private final String reason;

    public InternalSignException(String reason) {
        super(reason, null, false, false);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
