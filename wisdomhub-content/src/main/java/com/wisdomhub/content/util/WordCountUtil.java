package com.wisdomhub.content.util;

/**
 * 字数统计。中英混排下"字数"没有唯一答案，这里定的口径是：
 * <ul>
 *   <li>CJK 字符（含日文假名、韩文音节）每个记 1</li>
 *   <li>其余连续的非 CJK 字母数字算一个单词，记 1</li>
 *   <li>空白与标点不计</li>
 * </ul>
 * 入参应当是渲染后的**纯文本**，不是 Markdown 原文 —— 否则 {@code #}、{@code ```}、
 * 图片 URL 里的字符都会被算进去。
 */
public final class WordCountUtil {

    private WordCountUtil() {
    }

    public static int count(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return 0;
        }
        int count = 0;
        int i = 0;
        int length = plainText.length();
        while (i < length) {
            int codePoint = plainText.codePointAt(i);
            if (isCjk(codePoint)) {
                count++;
                i += Character.charCount(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                count++;
                i += Character.charCount(codePoint);
                while (i < length) {
                    int next = plainText.codePointAt(i);
                    if (isCjk(next) || !Character.isLetterOrDigit(next)) {
                        break;
                    }
                    i += Character.charCount(next);
                }
            } else {
                i += Character.charCount(codePoint);
            }
        }
        return count;
    }

    private static boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)     // 统一表意文字
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)  // 扩展 A
                || (codePoint >= 0x20000 && codePoint <= 0x2A6DF) // 扩展 B
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)  // 兼容表意文字
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)  // 日文假名
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF); // 韩文音节
    }
}
