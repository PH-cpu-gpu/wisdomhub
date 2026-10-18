package com.wisdomhub.recommendation.constants;

public final class RecommendationConstants {

    public static final int DEFAULT_LIMIT = 20;

    /** 与 search 召回接口的上限保持一致 */
    public static final int MAX_LIMIT = 50;

    /** 首页推荐取画像前几个标签召回 */
    public static final int HOME_PROFILE_TAG_LIMIT = 5;

    /** 进入打分前先多召回一些候选 */
    public static final int RECALL_LIMIT = 50;

    public static int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private RecommendationConstants() {
    }
}
