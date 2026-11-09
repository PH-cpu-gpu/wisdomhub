package com.wisdomhub.recommendation.service.impl;

import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.recommendation.api.interaction.dto.InteractionCountVO;
import com.wisdomhub.recommendation.api.search.SearchFeign;
import com.wisdomhub.recommendation.api.search.dto.ArticleRecallDTO;
import com.wisdomhub.recommendation.api.search.dto.HotRequest;
import com.wisdomhub.recommendation.api.search.dto.RecallRequest;
import com.wisdomhub.recommendation.api.search.dto.SimilarRequest;
import com.wisdomhub.recommendation.api.search.dto.TagBriefDTO;
import com.wisdomhub.recommendation.api.search.dto.VideoRecallDTO;
import com.wisdomhub.recommendation.config.RecommendationMixProperties;
import com.wisdomhub.recommendation.constants.RecommendationConstants;
import com.wisdomhub.recommendation.domain.vo.InterestProfileVO;
import com.wisdomhub.recommendation.domain.vo.RecommendationItemVO;
import com.wisdomhub.recommendation.domain.vo.RecommendationTagVO;
import com.wisdomhub.recommendation.domain.vo.ScoredArticleVO;
import com.wisdomhub.recommendation.domain.vo.ScoredVideoVO;
import com.wisdomhub.recommendation.domain.vo.TagWeightVO;
import com.wisdomhub.recommendation.service.InteractionCountQueryService;
import com.wisdomhub.recommendation.service.InterestProfileService;
import com.wisdomhub.recommendation.service.RecommendationScorer;
import com.wisdomhub.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final SearchFeign searchFeign;
    private final InterestProfileService interestProfileService;
    private final RecommendationScorer recommendationScorer;
    private final InteractionCountQueryService interactionCountQueryService;
    private final RecommendationMixProperties mixProperties;

    @Override
    @Cacheable(cacheNames = "home",
            key = "#userId == null ? 'anon:' + #limit + ':v2' : 'u' + #userId + ':' + #limit + ':v2'",
            unless = "#result == null || #result.isEmpty()")
    public List<RecommendationItemVO> home(Long userId, Integer limit) {
        int videoQuota = videoQuota(limit);
        if (userId == null) {
            return hotHome(limit, videoQuota);
        }
        InterestProfileVO profile = interestProfileService.getProfile(userId);
        List<Long> tagIds = topTagIds(profile);
        if (tagIds.isEmpty()) {
            return hotHome(limit, videoQuota);
        }

        List<ScoredArticleVO> scoredArticles = recommendationScorer.score(
                dedupe(recallArticles(tagIds, limit)), userId, profile);
        if (videoQuota == 0) {
            return toItems(scoredArticles.stream().limit(limit).map(ScoredArticleVO::getArticle).toList());
        }
        List<ScoredVideoVO> scoredVideos = recommendationScorer.scoreVideos(
                recallVideos(tagIds, limit), profile);
        return merge(scoredArticles, scoredVideos, limit, videoQuota);
    }

    @Override
    @Cacheable(cacheNames = "related",
            key = "#articleId + ':' + (#userId == null ? 'anon' : 'u' + #userId) + ':' + #limit + ':v2'",
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
            key = "(#tagId == null ? 'all' : #tagId) + ':' + #limit + ':v2'",
            unless = "#result == null || #result.isEmpty()")
    public List<RecommendationItemVO> hot(Long tagId, Integer limit) {
        return toItems(searchFeign.hot(hotRequest(tagId, limit)));
    }

    // ==================== 召回 ====================

    private List<ArticleRecallDTO> recallArticles(List<Long> tagIds, int limit) {
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
        return candidates;
    }

    /** 视频召回失败时降级为空，首页继续用文章补齐 */
    private List<VideoRecallDTO> recallVideos(List<Long> tagIds, int limit) {
        List<VideoRecallDTO> candidates = new ArrayList<>();
        try {
            RecallRequest recallRequest = new RecallRequest();
            recallRequest.setTagIds(tagIds);
            recallRequest.setLimit(RecommendationConstants.RECALL_LIMIT);
            candidates.addAll(searchFeign.videoRecall(recallRequest));
        } catch (Exception e) {
            log.warn("视频标签召回失败，首页降级为纯文章", e);
            return List.of();
        }
        if (candidates.size() < limit) {
            try {
                HotRequest hotRequest = new HotRequest();
                hotRequest.setExcludeIds(candidates.stream().map(VideoRecallDTO::getVideoId)
                        .filter(Objects::nonNull).toList());
                hotRequest.setLimit(RecommendationConstants.RECALL_LIMIT);
                candidates.addAll(searchFeign.videoHot(hotRequest));
            } catch (Exception e) {
                log.warn("热门视频补齐失败，仅使用标签召回结果", e);
            }
        }
        return dedupeVideos(candidates);
    }

    private List<RecommendationItemVO> hotHome(int limit, int videoQuota) {
        if (videoQuota == 0) {
            return toItems(searchFeign.hot(hotRequest(null, limit)));
        }
        List<ArticleRecallDTO> articles = searchFeign.hot(hotRequest(null, RecommendationConstants.RECALL_LIMIT));
        List<VideoRecallDTO> videos;
        try {
            videos = searchFeign.videoHot(hotRequest(null, RecommendationConstants.RECALL_LIMIT));
        } catch (Exception e) {
            log.warn("热门视频召回失败，首页降级为纯文章", e);
            videos = List.of();
        }
        int videoTake = Math.min(videoQuota, videos.size());
        int articleTake = Math.max(0, limit - videoTake);
        return mergeHot(articles.stream().limit(articleTake).toList(),
                videos.stream().limit(videoTake).toList());
    }

    // ==================== 混合与出参 ====================

    private List<RecommendationItemVO> merge(List<ScoredArticleVO> scoredArticles,
                                             List<ScoredVideoVO> scoredVideos, int limit, int videoQuota) {
        List<ScoredVideoVO> pickedVideos = scoredVideos.stream().limit(videoQuota).toList();
        int articleTake = Math.max(0, limit - pickedVideos.size());
        List<ScoredArticleVO> pickedArticles = scoredArticles.stream().limit(articleTake).toList();

        List<RankedCandidate> ranked = new ArrayList<>(pickedArticles.size() + pickedVideos.size());
        for (ScoredArticleVO scored : pickedArticles) {
            ArticleRecallDTO article = scored.getArticle();
            ranked.add(new RankedCandidate(scored.getTotalScore(), article.getPublishTime(),
                    article.getArticleId(), article, null));
        }
        for (ScoredVideoVO scored : pickedVideos) {
            VideoRecallDTO video = scored.getVideo();
            ranked.add(new RankedCandidate(scored.getTotalScore(), video.getPublishTime(),
                    video.getVideoId(), null, video));
        }
        return toMixedItems(ranked);
    }

    private List<RecommendationItemVO> mergeHot(List<ArticleRecallDTO> articles, List<VideoRecallDTO> videos) {
        List<RankedCandidate> ranked = new ArrayList<>(articles.size() + videos.size());
        for (ArticleRecallDTO article : articles) {
            ranked.add(new RankedCandidate(nz(article.getViewCount()), article.getPublishTime(),
                    article.getArticleId(), article, null));
        }
        for (VideoRecallDTO video : videos) {
            ranked.add(new RankedCandidate(nz(video.getViewCount()), video.getPublishTime(),
                    video.getVideoId(), null, video));
        }
        return toMixedItems(ranked);
    }

    private List<RecommendationItemVO> toMixedItems(List<RankedCandidate> ranked) {
        ranked.sort(Comparator.comparingDouble(RankedCandidate::score).reversed()
                .thenComparing(RankedCandidate::publishTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedCandidate::id,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        List<Long> articleIds = ranked.stream().map(RankedCandidate::article).filter(Objects::nonNull)
                .map(ArticleRecallDTO::getArticleId).filter(Objects::nonNull).toList();
        List<Long> videoIds = ranked.stream().map(RankedCandidate::video).filter(Objects::nonNull)
                .map(VideoRecallDTO::getVideoId).filter(Objects::nonNull).toList();
        Map<Long, InteractionCountVO> articleCounts = interactionCountQueryService.byTargetIds(
                ContentTargetTypeConstants.ARTICLE, articleIds);
        Map<Long, InteractionCountVO> videoCounts = interactionCountQueryService.byTargetIds(
                ContentTargetTypeConstants.VIDEO, videoIds);

        List<RecommendationItemVO> items = new ArrayList<>(ranked.size());
        for (RankedCandidate candidate : ranked) {
            if (candidate.article() != null) {
                items.add(toArticleItem(candidate.article(), articleCounts.get(candidate.id())));
            } else {
                items.add(toVideoItem(candidate.video(), videoCounts.get(candidate.id())));
            }
        }
        return items;
    }

    private List<RecommendationItemVO> toItems(List<ArticleRecallDTO> articles) {
        List<Long> articleIds = articles.stream().map(ArticleRecallDTO::getArticleId)
                .filter(Objects::nonNull).toList();
        Map<Long, InteractionCountVO> counts = interactionCountQueryService.byTargetIds(
                ContentTargetTypeConstants.ARTICLE, articleIds);
        List<RecommendationItemVO> items = new ArrayList<>(articles.size());
        for (ArticleRecallDTO article : articles) {
            items.add(toArticleItem(article,
                    article.getArticleId() == null ? null : counts.get(article.getArticleId())));
        }
        return items;
    }

    private RecommendationItemVO toArticleItem(ArticleRecallDTO article, InteractionCountVO count) {
        return RecommendationItemVO.builder()
                .contentType(ContentTargetTypeConstants.ARTICLE)
                .articleId(article.getArticleId())
                .authorId(article.getAuthorId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .tags(toTags(article.getTags()))
                .publishTime(article.getPublishTime())
                .viewCount(article.getViewCount())
                .likeCount(count == null ? 0 : nvl(count.getLikeCount()))
                .commentCount(count == null ? 0 : nvl(count.getCommentCount()))
                .favoriteCount(count == null ? 0 : nvl(count.getFavoriteCount()))
                .build();
    }

    private RecommendationItemVO toVideoItem(VideoRecallDTO video, InteractionCountVO count) {
        return RecommendationItemVO.builder()
                .contentType(ContentTargetTypeConstants.VIDEO)
                .videoId(video.getVideoId())
                .authorId(video.getAuthorId())
                .title(video.getTitle())
                .summary(video.getSummary())
                .tags(toTags(video.getTags()))
                .coverKey(video.getCoverKey())
                .coverUrl(video.getVideoId() == null ? null : "/api/video/" + video.getVideoId() + "/cover")
                .durationMs(video.getDurationMs())
                .publishTime(video.getPublishTime())
                .viewCount(video.getViewCount())
                .likeCount(count == null ? 0 : nvl(count.getLikeCount()))
                .commentCount(count == null ? 0 : nvl(count.getCommentCount()))
                .favoriteCount(count == null ? 0 : nvl(count.getFavoriteCount()))
                .build();
    }

    // ==================== 共用 ====================

    private HotRequest hotRequest(Long tagId, Integer limit) {
        HotRequest request = new HotRequest();
        request.setTagId(tagId);
        request.setLimit(limit);
        return request;
    }

    private int videoQuota(int limit) {
        int quota = (int) Math.round(limit * mixProperties.getVideoShare());
        return Math.max(0, Math.min(limit, quota));
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

    private List<VideoRecallDTO> dedupeVideos(List<VideoRecallDTO> candidates) {
        Map<Long, VideoRecallDTO> byId = new LinkedHashMap<>();
        for (VideoRecallDTO candidate : candidates) {
            if (candidate.getVideoId() != null) {
                byId.putIfAbsent(candidate.getVideoId(), candidate);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private double nz(Integer value) {
        return value == null ? 0 : value;
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

    private record RankedCandidate(double score, LocalDateTime publishTime, Long id,
                                   ArticleRecallDTO article, VideoRecallDTO video) {
    }
}
