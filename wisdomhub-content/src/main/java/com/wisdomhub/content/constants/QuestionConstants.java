package com.wisdomhub.content.constants;

public class QuestionConstants {

    private QuestionConstants() {}

    /** 问题列表排序白名单 */
    public static final String SORT_NEW = "new";
    public static final String SORT_HOT = "hot";

    public static final Integer NOT_DELETED = 0;
    public static final Integer DELETED = 1;

    /** 正文长度上限。列是 TEXT（65535 字节），这里挡在应用层，避免超长请求打到数据库 */
    public static final int CONTENT_MAX = 10000;
}
