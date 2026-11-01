package com.wisdomhub.content.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 标签名归一化：NFKC、小写、去掉空白与常见分隔符。标签名、别名、关系端点的查重都走这里。
 */
public final class TagNameNormalizer {

    public static final int MAX_LENGTH = 50;

    private static final Pattern SEPARATOR = Pattern.compile("[\\s\\-_·.]+");

    private TagNameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String value = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
        return SEPARATOR.matcher(value).replaceAll("");
    }
}
