package com.wisdomhub.search.service;

import cn.hutool.core.util.StrUtil;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.search.constants.SearchConstants;
import com.wisdomhub.search.constants.SearchRedisConstants;
import com.wisdomhub.search.domain.vo.HotWordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 搜索热词：按日一个 zset 累加，读的时候合并最近 N 天。
 * 两个方法都吞掉 Redis 异常 —— 热搜是页面上的装饰，不能把搜索带崩。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotWordService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;

    /** 记一次搜索。空词、超长词都不进榜 */
    public void record(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return;
        }
        String word = keyword.trim();
        if (word.length() > SearchConstants.MAX_HOT_WORD_LENGTH) {
            return;
        }
        String key = dailyKey(LocalDate.now());
        try {
            stringRedisTemplate.opsForZSet().incrementScore(key, word, 1);
            stringRedisTemplate.expire(key, RedisTtl.SEARCH_HOT);
        } catch (Exception e) {
            log.warn("热搜词记录失败, keyword={}", word, e);
        }
    }

    /** 最近 days 天的热词榜。合并结果按天数缓存，到点由 TTL 自然重算 */
    public List<HotWordVO> top(Integer days, Integer size) {
        int span = SearchConstants.clampHotDays(days);
        int limit = SearchConstants.clampHotSize(size);
        String destKey = SearchRedisConstants.HOT_WORD_UNION_ZSET + span;
        try {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(destKey))) {
                List<String> dailyKeys = dailyKeys(LocalDate.now(), span);
                stringRedisTemplate.opsForZSet()
                        .unionAndStore(dailyKeys.getFirst(), dailyKeys.subList(1, span), destKey);
                stringRedisTemplate.expire(destKey, RedisTtl.HOT_RANK);
            }
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeWithScores(destKey, 0, limit - 1);
            return tuples == null ? List.of() : tuples.stream()
                    .map(tuple -> HotWordVO.builder()
                            .keyword(tuple.getValue())
                            .count(tuple.getScore() == null ? 0 : tuple.getScore().intValue())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("热搜词读取失败, days={}", span, e);
            return List.of();
        }
    }

    private String dailyKey(LocalDate date) {
        return SearchRedisConstants.HOT_WORD_DAILY_ZSET + date.format(DAY_FORMAT);
    }

    private List<String> dailyKeys(LocalDate today, int span) {
        return IntStream.range(0, span).mapToObj(i -> dailyKey(today.minusDays(i))).toList();
    }
}
