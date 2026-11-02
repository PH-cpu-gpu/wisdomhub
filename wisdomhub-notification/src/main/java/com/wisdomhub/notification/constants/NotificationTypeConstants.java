package com.wisdomhub.notification.constants;

/**
 * notification.type 取值，与 25-notification.sql 的列注释一致。
 */
public final class NotificationTypeConstants {

    public static final int LIKE = 1;
    public static final int COMMENT = 2;
    public static final int ANSWER = 3;
    public static final int FOLLOW = 4;
    public static final int COLUMN_PURCHASE = 5;
    public static final int AI_ANALYSIS = 6;
    public static final int SYSTEM = 7;
    /** 标签/关系提案待审，发给所有管理员 */
    public static final int TAG_PROPOSAL = 8;

    private NotificationTypeConstants() {
    }
}
