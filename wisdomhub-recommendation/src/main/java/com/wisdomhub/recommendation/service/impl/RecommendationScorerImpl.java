package com.wisdomhub.recommendation.service.impl;

import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;
import com.wisdomhub.recommendation.api.search.dto.TagBriefDTO;
import com.wisdomhub.recommendation.config.ScoringProperties;
import com.wisdomhub.recommendation.domain.vo.InterestProfileVO;
import com.wisdomhub.recommendation.domain.vo.ScoredArticleVO;
import com.wisdomhub.recommendation.service.CollaborativeFilterScorer;
import com.wisdomhub.recommendation.service.RecommendationScorer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class RecommendationScorerImpl implements RecommendationScorer {

    private final ScoringProperties properties;

    private final CollaborativeFilterScorer cfScorer;

    public RecommendationScorerImpl(ScoringProperties properties,
                                    ObjectProvider<CollaborativeFilterScorer> cfScorerProvider) {
        this.properties = properties;
        this.cfScorer = cfScorerProvider.getIfAvailable(NoOpCollaborativeFilterScorer::new);
    }

    @Override
    public List<ScoredArticleVO> score(List<ArticleRecallDTO> candidates, Long userId, InterestProfileVO profile) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> normalizedProfile = normalizeProfile(profile);
        double maxEngagement = candidates.stream().mapToDouble(this::engagement).max().orElse(0);
        double weightSum = properties.getInterestWeight() + properties.getHotWeight()
                + properties.getFreshnessWeight() + properties.getCfWeight();

        List<ScoredArticleVO> scored = candidates.stream()
                .map(candidate -> doScore(candidate, userId, normalizedProfile, maxEngagement, weightSum))
                .sorted(comparator())
                .toList();
        return scored;
    }

    private ScoredArticleVO doScore(ArticleRecallDTO candidate, Long userId, Map<Long, Double> profile,
                                    double maxEngagement, double weightSum) {
        double interest = interestScore(candidate, profile);
        double hot = maxEngagement <= 0 ? 0 : Math.log1p(engagement(candidate)) / Math.log1p(maxEngagement);
        double freshness = freshnessScore(candidate.getPublishTime());
        double cf = clamp(cfScorer.score(userId, candidate));

        double total = (properties.getInterestWeight() * interest
                + properties.getHotWeight() * hot
                + properties.getFreshnessWeight() * freshness
                + properties.getCfWeight() * cf) / weightSum;

        return ScoredArticleVO.builder()
                .article(candidate)
                .totalScore(total)
                .interestScore(interest)
                .hotScore(hot)
                .freshnessScore(freshness)
                .cfScore(cf)
                .build();
    }

    /** 画像先按用户最高分归一化；候选按全部标签数取平均，堆无关标签会被稀释 */
    private double interestScore(ArticleRecallDTO candidate, Map<Long, Double> profile) {
        if (profile.isEmpty() || candidate.getTags() == null || candidate.getTags().isEmpty()) {
            return 0;
        }
        Set<Long> tagIds = new HashSet<>();
        for (TagBriefDTO tag : candidate.getTags()) {
            if (tag.getTagId() != null) {
                tagIds.add(tag.getTagId());
            }
        }
        if (tagIds.isEmpty()) {
            return 0;
        }
        double sum = tagIds.stream().mapToDouble(tagId -> profile.getOrDefault(tagId, 0.0)).sum();
        return sum / tagIds.size();
    }

    private Map<Long, Double> normalizeProfile(InterestProfileVO profile) {
        if (profile == null || profile.getTags() == null || profile.getTags().isEmpty()) {
            return Map.of();
        }
        double max = profile.getTags().stream()
                .filter(tag -> tag.getScore() != null && tag.getScore() > 0)
                .mapToDouble(tag -> tag.getScore())
                .max().orElse(0);
        if (max <= 0) {
            return Map.of();
        }
        Map<Long, Double> normalized = new HashMap<>();
        for (var tag : profile.getTags()) {
            if (tag.getTagId() != null && tag.getScore() != null && tag.getScore() > 0) {
                normalized.put(tag.getTagId(), tag.getScore() / max);
            }
        }
        return normalized;
    }

    private double engagement(ArticleRecallDTO candidate) {
        return nz(candidate.getViewCount()) * properties.getViewWeight()
                + nz(candidate.getLikeCount()) * properties.getLikeWeight()
                + nz(candidate.getCommentCount()) * properties.getCommentWeight()
                + nz(candidate.getFavoriteCount()) * properties.getFavoriteWeight();
    }

    private double freshnessScore(LocalDateTime publishTime) {
        if (publishTime == null) {
            return 0;
        }
        double ageDays = Math.max(0, Duration.between(publishTime, LocalDateTime.now()).toDays());
        return Math.pow(0.5, ageDays / properties.getFreshnessHalfLifeDays());
    }

    /** 总分降序 → 发布时间降序 → articleId 降序，保证同分稳定 */
    private Comparator<ScoredArticleVO> comparator() {
        return Comparator.comparingDouble(ScoredArticleVO::getTotalScore).reversed()
                .thenComparing(vo -> vo.getArticle().getPublishTime(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(vo -> vo.getArticle().getArticleId(),
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private double nz(Integer value) {
        return value == null ? 0 : value;
    }

    private double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}
