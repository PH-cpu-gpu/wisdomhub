package com.wisdomhub.recommendation.constants;

public final class InterestProfileConstants {

    public static final double WEIGHT_VIEW = 1;

    public static final double WEIGHT_LIKE = 3;

    public static final double WEIGHT_COLLECT = 5;

    public static final double WEIGHT_SEARCH = 2;

    /** 当月 / 上月 / 上上月 */
    public static final double[] MONTH_FACTORS = {1.0, 0.5, 0.25};

    /** 一次搜索最多命中几个标签 */
    public static final int SEARCH_TAG_LIMIT = 3;

    private InterestProfileConstants() {
    }
}
