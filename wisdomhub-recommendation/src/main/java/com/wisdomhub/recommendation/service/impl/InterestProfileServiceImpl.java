package com.wisdomhub.recommendation.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wisdomhub.common.mq.constants.BehaviorTypeConstants;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import com.wisdomhub.recommendation.api.content.ContentFeign;
import com.wisdomhub.recommendation.api.content.dto.TagDTO;
import com.wisdomhub.recommendation.constants.InterestProfileConstants;
import com.wisdomhub.recommendation.constants.RecommendationRedisConstants;
import com.wisdomhub.recommendation.domain.vo.InterestProfileVO;
import com.wisdomhub.recommendation.domain.vo.TagWeightVO;
import com.wisdomhub.recommendation.service.InterestProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestProfileServiceImpl implements InterestProfileService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final String TOKEN_SEPARATOR = "[\\s\\p{P}]+";

    private final StringRedisTemplate stringRedisTemplate;

    private final ContentFeign contentFeign;

    /** 标签字典按名查 id，变化极少，本地缓存 5 分钟 */
    private final Cache<String, Map<String, Long>> tagNameCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1)
            .build();

    @Override
    public void record(BehaviorRecordedPayload payload) {
        double weight = weightOf(payload.getBehaviorType());
        if (weight <= 0) {
            log.warn("未知行为类型, 跳过画像: {}", payload.getBehaviorType());
            return;
        }
        List<Long> tagIds = resolveTagIds(payload);
        if (tagIds.isEmpty()) {
            return;
        }
        String key = profileKey(payload.getUserId(), LocalDate.now());
        for (Long tagId : tagIds) {
            stringRedisTemplate.opsForZSet().incrementScore(key, tagId.toString(), weight);
        }
        stringRedisTemplate.expire(key, RecommendationRedisConstants.PROFILE_BUCKET_TTL);
    }

    @Override
    public InterestProfileVO getProfile(Long userId) {
        Map<Long, Double> merged = new HashMap<>();
        LocalDate now = LocalDate.now();
        for (int i = 0; i < InterestProfileConstants.MONTH_FACTORS.length; i++) {
            String key = profileKey(userId, now.minusMonths(i));
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);
            if (tuples == null) {
                continue;
            }
            double factor = InterestProfileConstants.MONTH_FACTORS[i];
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() == null || tuple.getScore() == null) {
                    continue;
                }
                merged.merge(Long.valueOf(tuple.getValue()), tuple.getScore() * factor, Double::sum);
            }
        }
        List<TagWeightVO> tags = new ArrayList<>(merged.size());
        merged.forEach((tagId, score) -> tags.add(TagWeightVO.builder().tagId(tagId).score(score).build()));
        tags.sort(Comparator.comparing(TagWeightVO::getScore).reversed());
        return InterestProfileVO.builder().tags(tags).build();
    }

    private List<Long> resolveTagIds(BehaviorRecordedPayload payload) {
        if (BehaviorTypeConstants.SEARCH.equals(payload.getBehaviorType())) {
            return searchTagIds(payload);
        }
        if (payload.getTagIds() == null) {
            return List.of();
        }
        return payload.getTagIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    /** 搜索：tagId 过滤直接命中；关键词按词与标签名精确匹配，最多 3 个 */
    private List<Long> searchTagIds(BehaviorRecordedPayload payload) {
        LinkedHashSet<Long> tagIds = new LinkedHashSet<>();
        if (payload.getTagId() != null) {
            tagIds.add(payload.getTagId());
        }
        if (StrUtil.isNotBlank(payload.getKeyword())) {
            Map<String, Long> tagIdByName = tagIdByName();
            for (String token : payload.getKeyword().trim().split(TOKEN_SEPARATOR)) {
                if (token.isBlank()) {
                    continue;
                }
                Long tagId = tagIdByName.get(token.toLowerCase(Locale.ROOT));
                if (tagId != null) {
                    tagIds.add(tagId);
                }
                if (tagIds.size() >= InterestProfileConstants.SEARCH_TAG_LIMIT) {
                    break;
                }
            }
        }
        return List.copyOf(tagIds);
    }

    /** content 不可用时返回空表且不缓存，下次事件重试 */
    private Map<String, Long> tagIdByName() {
        Map<String, Long> cached = tagNameCache.getIfPresent("all");
        if (cached != null) {
            return cached;
        }
        try {
            Map<String, Long> loaded = new HashMap<>();
            for (TagDTO tag : contentFeign.listAllTags()) {
                if (tag.getName() != null) {
                    loaded.put(tag.getName().trim().toLowerCase(Locale.ROOT), tag.getTagId());
                }
            }
            tagNameCache.put("all", loaded);
            return loaded;
        } catch (Exception e) {
            log.warn("标签字典拉取失败, 本次搜索不做标签映射", e);
            return Map.of();
        }
    }

    private double weightOf(String behaviorType) {
        return switch (behaviorType) {
            case BehaviorTypeConstants.VIEW -> InterestProfileConstants.WEIGHT_VIEW;
            case BehaviorTypeConstants.LIKE -> InterestProfileConstants.WEIGHT_LIKE;
            case BehaviorTypeConstants.COLLECT -> InterestProfileConstants.WEIGHT_COLLECT;
            case BehaviorTypeConstants.SEARCH -> InterestProfileConstants.WEIGHT_SEARCH;
            default -> 0;
        };
    }

    private String profileKey(Long userId, LocalDate month) {
        return RecommendationRedisConstants.PROFILE_TAG_KEY + userId + ":" + month.format(MONTH_FORMAT);
    }
}
