package com.wisdomhub.common.security.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 签名串 = METHOD\nPATH?RAW_QUERY\nTIMESTAMP\nNONCE\nSHA256_HEX(body)。
 */
public final class InternalSignUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] EMPTY_BODY = new byte[0];

    private InternalSignUtil() {
    }

    public static String canonicalString(String method, String pathWithQuery, String timestamp, String nonce, byte[] body) {
        return method.toUpperCase(Locale.ROOT) + "\n"
                + pathWithQuery + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + sha256Hex(body == null ? EMPTY_BODY : body);
    }

    public static String sign(String canonicalString, String secret) {
        return Base64.getEncoder().encodeToString(hmac(canonicalString, secret));
    }

    /** 恒定时间比较；签名非法按失败处理，不抛异常。 */
    public static boolean verify(String canonicalString, String secret, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            return MessageDigest.isEqual(hmac(canonicalString, secret), Base64.getDecoder().decode(signature));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static byte[] hmac(String canonicalString, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }
}
