package com.wisdomhub.recommendation.service.impl;

import com.wisdomhub.recommendation.api.search.SearchFeign;
import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;
import com.wisdomhub.recommendation.api.search.dto.HotRequest;
import com.wisdomhub.recommendation.api.search.dto.RecallRequest;
import com.wisdomhub.recommendation.api.search.dto.SimilarRequest;
import com.wisdomhub.recommendation.api.search.dto.TagBriefDTO;
import com.wisdomhub.recommendation.constants.RecommendationConstants;
import com.wisdomhub.recommendation.domain.vo.InterestProfileVO;
import com.wisdomhub.recommendation.domain.vo.RecommendationItemVO;
import com.wisdomhub.recommendation.domain.vo.RecommendationTagVO;
import com.wisdomhub.recommendation.domain.vo.ScoredArticleVO;
import com.wisdomhub.recommendation.domain.vo.TagWeightVO;
import com.wisdomhub.recommendation.service.InterestProfileService;
import com.wisdomhub.recommendation.service.RecommendationScorer;
import com.wisdomhub.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final SearchFeign searchFeign;
    private final InterestProfileService interestProfileService;
    private final RecommendationScorer recommendationScorer;

    @Override
    @Cacheable(cacheNames = "home",
            key = "#userId == null ? 'anon:' + #limit : 'u' + #userId + ':' + #limit",
            unless = "#result == null || #result.isEmpty()")
    public List<RecommendationItemVO> home(Long userId, Integer limit) {
        if (userId == null) {
            return toItems(searchFeign.hot(hotRequest(null, limit)));
        }
        InterestProfileVO profile = interestProfileService.getProfile(userId);
        List<Long> tagIds = topTagIds(profile);
        if (tagIds.isEmpty()) {
            return toItems(searchFeign.hot(hotRequest(null, limit)));
        }

        RecallRequest recallRequest = new RecallRequest();
        recallRequest.setTagIds(tagIds);
        recallRequest.setLimit(RecommendationConstants.RECALL_LIMIT);
        List<ArticleRecallDTO> candidates = new ArrayList<>(searchFeign.recall(recallRequest));
        if (candidates.size() < limit) {
            HotRequest hotRequest = new HotRequest();
            hotRequest.setExcludeIds(candidates.stream().map(ArticleRecallDTO::getArticleId)
                    .filter(Objects::nonNull).toList());
            hotRequest.setLimit(RecommendationConstants.RECALL_LIMIT);
            candidates.addAll(searchFeign.hot(hotRequest));
        }

        List<ScoredArticleVO> scored = recommendationScorer.score(dedupe(candidates), userId, profile);
        return toItems(scored.stream().limit(limit).map(ScoredArticleVO::getArticle).toList());
    }

    @Override
    @Cacheable(cacheNames = "related",
            key = "#articleId + ':' + (#userId == null ? 'anon' : 'u' + #userId) + ':' + #limit",
            unless = "#result == null || #result.isEmpty()")
    public List<RecommendationItemVO> related(Long articleId, Long userId, Integer limit) {
        SimilarRequest similarRequest = new SimilarRequest();
        similarRequest.setArticleId(articleId);
        similarRequest.setLimit(RecommendationConstants.RECALL_LIMIT);
        List<ArticleRecallDTO> candidates = searchFeign.similar(similarRequest);
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }
        InterestProfileVO profile = userId == null ? null : interestProfileService.getProfile(userId);
        List<ScoredArticleVO> scored = recommendationScorer.score(candidates, userId, profile);
        return toItems(scored.stream().limit(limit).map(ScoredArticleVO::getArticle).toList());
    }

    @Override
    @Cacheable(cacheNames = "hot",
            key = "(#tagId == null ? 'all' : #tagId) + ':' + #limit",
            unless = "#result == null || #result.isEmpty()")
    public List<RecommendationItemVO> hot(Long tagId, Integer limit) {
        return toItems(searchFeign.hot(hotRequest(tagId, limit)));
    }

    private HotRequest hotRequest(Long tagId, Integer limit) {
        HotRequest request = new HotRequest();
        request.setTagId(tagId);
        request.setLimit(limit);
        return request;
    }

    private List<Long> topTagIds(InterestProfileVO profile) {
        if (profile == null || profile.getTags() == null) {
            return List.of();
        }
        return profile.getTags().stream()
                .filter(tag -> tag.getTagId() != null && tag.getScore() != null && tag.getScore() > 0)
                .limit(RecommendationConstants.HOME_PROFILE_TAG_LIMIT)
                .map(TagWeightVO::getTagId)
                .toList();
    }

    private List<ArticleRecallDTO> dedupe(List<ArticleRecallDTO> candidates) {
        Map<Long, ArticleRecallDTO> byId = new LinkedHashMap<>();
        for (ArticleRecallDTO candidate : candidates) {
            if (candidate.getArticleId() != null) {
                byId.putIfAbsent(candidate.getArticleId(), candidate);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /** 缓存值必须是 ArrayList，不能用 List.of / toList 这类不可变集合 */
    private List<RecommendationItemVO> toItems(List<ArticleRecallDTO> articles) {
        List<RecommendationItemVO> items = new ArrayList<>(articles.size());
        for (ArticleRecallDTO article : articles) {
            items.add(RecommendationItemVO.builder()
                    .articleId(article.getArticleId())
                    .authorId(article.getAuthorId())
                    .title(article.getTitle())
                    .summary(article.getSummary())
                    .tags(toTags(article.getTags()))
                    .publishTime(article.getPublishTime())
                    .viewCount(article.getViewCount())
                    .likeCount(article.getLikeCount())
                    .commentCount(article.getCommentCount())
                    .favoriteCount(article.getFavoriteCount())
                    .build());
        }
        return items;
    }

    private List<RecommendationTagVO> toTags(List<TagBriefDTO> tags) {
        List<RecommendationTagVO> result = new ArrayList<>(tags == null ? 0 : tags.size());
        if (tags == null) {
            return result;
        }
        for (TagBriefDTO tag : tags) {
            result.add(RecommendationTagVO.builder()
                    .tagId(tag.getTagId())
                    .name(tag.getName())
                    .category(tag.getCategory())
                    .build());
        }
        return result;
    }
}
