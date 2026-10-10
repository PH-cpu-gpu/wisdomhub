package com.wisdomhub.content.constants;

import com.wisdomhub.common.redis.constants.RedisPrefix;

public class QuestionRedisKeyConstants {

    private QuestionRedisKeyConstants() {}

    /**
     * 回答点赞 / 问题收藏的防重前置拦截，TTL = RedisTtl.LIKE_ONCE。
     * 与 B1.26 同构，只是快路径：正确性由 uk_user_answer / uk_user_question +
     * UPDATE 的 affected 行数保证，Redis 不可用时放行
     */
    public static final String ANSWER_LIKE_ONCE_KEY = RedisPrefix.CONTENT + "answer:like:once:";

    public static final String QUESTION_FAVORITE_ONCE_KEY = RedisPrefix.CONTENT + "question:favorite:once:";
}
