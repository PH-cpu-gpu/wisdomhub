package com.wisdomhub.common.core.constants;

/**
 * 互动目标类型，取值与 notification.target_type 对齐。
 * 数据库与公开接口用数字 code；/internal/targets 路径用 *_NAME。
 */
public final class ContentTargetTypeConstants {

    /** 文章 */
    public static final int ARTICLE = 1;
    /** 回答 */
    public static final int ANSWER = 2;
    /** 问题 */
    public static final int QUESTION = 3;
    /** 专栏 */
    public static final int COLUMN = 4;
    /** 用户 */
    public static final int USER = 5;
    /** 标签提案 */
    public static final int TAG_PROPOSAL = 6;
    /** 视频 */
    public static final int VIDEO = 7;

    public static final String ARTICLE_NAME = "ARTICLE";
    public static final String ANSWER_NAME = "ANSWER";
    public static final String QUESTION_NAME = "QUESTION";
    public static final String COLUMN_NAME = "COLUMN";
    public static final String USER_NAME = "USER";
    public static final String TAG_PROPOSAL_NAME = "TAG_PROPOSAL";
    public static final String VIDEO_NAME = "VIDEO";

    public static boolean isValid(int code) {
        return switch (code) {
            case ARTICLE, ANSWER, QUESTION, COLUMN, USER, TAG_PROPOSAL, VIDEO -> true;
            default -> false;
        };
    }

    public static String nameOf(int code) {
        return switch (code) {
            case ARTICLE -> ARTICLE_NAME;
            case ANSWER -> ANSWER_NAME;
            case QUESTION -> QUESTION_NAME;
            case COLUMN -> COLUMN_NAME;
            case USER -> USER_NAME;
            case TAG_PROPOSAL -> TAG_PROPOSAL_NAME;
            case VIDEO -> VIDEO_NAME;
            default -> throw new IllegalArgumentException("未知 targetType: " + code);
        };
    }

    public static int codeOf(String name) {
        return switch (name) {
            case ARTICLE_NAME -> ARTICLE;
            case ANSWER_NAME -> ANSWER;
            case QUESTION_NAME -> QUESTION;
            case COLUMN_NAME -> COLUMN;
            case USER_NAME -> USER;
            case TAG_PROPOSAL_NAME -> TAG_PROPOSAL;
            case VIDEO_NAME -> VIDEO;
            default -> throw new IllegalArgumentException("未知 targetType 名称: " + name);
        };
    }

    private ContentTargetTypeConstants() {
    }
}
