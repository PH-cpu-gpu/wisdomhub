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

    private NotificationTypeConstants() {
    }
}
