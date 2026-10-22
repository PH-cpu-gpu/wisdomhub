package com.wisdomhub.common.security.constants;

/**
 * /internal/** 服务签名契约常量，Java 与 Python 两侧必须逐字一致。
 */
public final class InternalSignConstants {

    public static final String HEADER_SERVICE_NAME = "X-Service-Name";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";

    public static final String PATH_PREFIX = "/internal";
    public static final String NONCE_KEY = "nonce:";

    public static final long DEFAULT_TIMESTAMP_WINDOW_SECONDS = 300;
    public static final long DEFAULT_NONCE_TTL_SECONDS = 300;

    private InternalSignConstants() {
    }
}
