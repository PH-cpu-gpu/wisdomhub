package com.wisdomhub.search.constants;

public final class SearchConstants {

    private SearchConstants() {}

    /** 排序白名单枚举。传了白名单外的值一律落回 relevance */
    public static final String SORT_RELEVANCE = "relevance";
    public static final String SORT_NEW = "new";
    public static final String SORT_HOT = "hot";

    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大页尺寸超过按 100 处理，不报错 */
    public static final int MAX_PAGE_SIZE = 100;

    /** ES 的 index.max_result_window 默认值，from + size 超了 ES 直接报错，所以在这里钳住 */
    public static final int MAX_RESULT_WINDOW = 10000;

    /** 正文片段长度。标题与摘要用 numberOfFragments=0 整段返回，只有正文截片段 */
    public static final int FRAGMENT_SIZE = 150;

    /** 多词查询的召回口径：纯 OR 会被长尾词淹掉，纯 AND 召回太窄 */
    public static final String MINIMUM_SHOULD_MATCH = "70%";

    /** 高亮标签 */
    public static final String HIGHLIGHT_PRE_TAG = "<em>";
    public static final String HIGHLIGHT_POST_TAG = "</em>";

    public static int clampPage(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    public static int clampPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** from 与 size 一起钳：翻到比 10000 还深没有意义，还会让 ES 报错 */
    public static int clampFrom(int page, int pageSize) {
        int from = (page - 1) * pageSize;
        return Math.min(from, MAX_RESULT_WINDOW - pageSize);
    }
}
