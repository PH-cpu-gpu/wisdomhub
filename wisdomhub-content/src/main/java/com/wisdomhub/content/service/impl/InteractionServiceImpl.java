package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.mq.constants.BehaviorTypeConstants;
import com.wisdomhub.common.mq.event.ArticleLikedEvent;
import com.wisdomhub.common.mq.event.BehaviorRecordedEvent;
import com.wisdomhub.common.mq.event.payload.ArticleLikedPayload;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.ArticleRedisKeyConstants;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.entity.ArticleFavorite;
import com.wisdomhub.content.domain.entity.ArticleLike;
import com.wisdomhub.content.domain.entity.ArticleTag;
import com.wisdomhub.content.mapper.ArticleFavoriteMapper;
import com.wisdomhub.content.mapper.ArticleLikeMapper;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.mapper.ArticleTagMapper;
import com.wisdomhub.content.service.InteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionServiceImpl implements InteractionService {

    private static final String LIKE_COLUMN = "like_count";
    private static final String FAVORITE_COLUMN = "favorite_count";

    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleFavoriteMapper articleFavoriteMapper;
    private final ArticleTagMapper articleTagMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final EventPublisher eventPublisher;

    // ==================== 点赞 ====================

    @Override
    @Transactional
    public int like(Long articleId) {
        Long userId = CurrentUserUtil.id();
        Article article = requireVisibleArticle(articleId);
        String gateKey = ArticleRedisKeyConstants.LIKE_ONCE_KEY + userId + ":" + articleId;
        if (!passGate(gateKey)) {
            // 窗口内已点过且没取消：直接返回，不碰 DB
            return article.getLikeCount();
        }
        try {
            articleLikeMapper.insert(ArticleLike.builder().userId(userId).articleId(articleId).build());
        } catch (DuplicateKeyException e) {
            return article.getLikeCount();
        }
        if (bumpCount(articleId, Article::getLikeCount, LIKE_COLUMN, 1) == 0) {
            // 校验之后文章被并发删/下架：抛出去回滚这次插入，并撤掉 gate，别把用户白拦一个窗口
            clearGate(gateKey);
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        sendLikeEvent(articleId, userId, article.getAuthorId());
        sendBehaviorEvent(articleId, userId, BehaviorTypeConstants.LIKE);
        return readCount(articleId, Article::getLikeCount);
    }

    @Override
    @Transactional
    public int unlike(Long articleId) {
        Long userId = CurrentUserUtil.id();
        Article article = requireVisibleArticle(articleId);
        // 先撤 gate：不撤的话用户在这个窗口内没法再点回来
        clearGate(ArticleRedisKeyConstants.LIKE_ONCE_KEY + userId + ":" + articleId);
        int deleted = articleLikeMapper.delete(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getUserId, userId)
                .eq(ArticleLike::getArticleId, articleId));
        if (deleted == 0) {
            // 本来就没点过：幂等（17.3），不是错误
            return article.getLikeCount();
        }
        bumpCount(articleId, Article::getLikeCount, LIKE_COLUMN, -1);
        return readCount(articleId, Article::getLikeCount);
    }

    // ==================== 收藏 ====================

    @Override
    @Transactional
    public int collect(Long articleId) {
        Long userId = CurrentUserUtil.id();
        Article article = requireVisibleArticle(articleId);
        String gateKey = ArticleRedisKeyConstants.FAVORITE_ONCE_KEY + userId + ":" + articleId;
        if (!passGate(gateKey)) {
            return article.getFavoriteCount();
        }
        try {
            articleFavoriteMapper.insert(ArticleFavorite.builder().userId(userId).articleId(articleId).build());
        } catch (DuplicateKeyException e) {
            return article.getFavoriteCount();
        }
        if (bumpCount(articleId, Article::getFavoriteCount, FAVORITE_COLUMN, 1) == 0) {
            clearGate(gateKey);
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        sendBehaviorEvent(articleId, userId, BehaviorTypeConstants.COLLECT);
        return readCount(articleId, Article::getFavoriteCount);
    }

    @Override
    @Transactional
    public int uncollect(Long articleId) {
        Long userId = CurrentUserUtil.id();
        Article article = requireVisibleArticle(articleId);
        clearGate(ArticleRedisKeyConstants.FAVORITE_ONCE_KEY + userId + ":" + articleId);
        int deleted = articleFavoriteMapper.delete(new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getUserId, userId)
                .eq(ArticleFavorite::getArticleId, articleId));
        if (deleted == 0) {
            return article.getFavoriteCount();
        }
        bumpCount(articleId, Article::getFavoriteCount, FAVORITE_COLUMN, -1);
        return readCount(articleId, Article::getFavoriteCount);
    }

    // ==================== 共用 ====================

    // 确保文章存在
    private Article requireVisibleArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null
                || article.getDeleted() == ArticleConstants.DELETED.byteValue()
                || article.getState() != ArticleConstants.PUBLISH.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    /**
     * 防重前置拦截：SETNX 成功 = 窗口内首次，失败 = 窗口内已点过。Redis 不可用时一律放行
     */
    private boolean passGate(String gateKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(gateKey, "1", RedisTtl.LIKE_ONCE));
        } catch (Exception e) {
            log.warn("防重前置拦截不可用, 放行由唯一索引兜底: key={}", gateKey, e);
            return true;
        }
    }

    /** 撤掉 gate：取消操作，或本次操作实际没生效。清不掉只记日志，TTL 会自愈 */
    private void clearGate(String gateKey) {
        try {
            stringRedisTemplate.delete(gateKey);
        } catch (Exception e) {
            log.warn("防重 gate 清除失败, 等 TTL 自愈: key={}", gateKey, e);
        }
    }

    /**
     * 计数原子增减，绝不读改写。不碰 version（乐观锁只由覆盖发布递增，这里动它会让并发发布误判冲突）、
     * 不 bump update_time。
     * delta 为负时带非负安全网：历史漂移时不要把计数减成负数
     */
    private int bumpCount(Long articleId, SFunction<Article, Integer> counter, String column, int delta) {
        LambdaUpdateWrapper<Article> wrapper = new LambdaUpdateWrapper<Article>()
                .setSql(column + " = " + column + " + " + delta)
                .eq(Article::getId, articleId)
                .eq(Article::getDeleted, ArticleConstants.NOT_DELETED)
                .eq(Article::getState, ArticleConstants.PUBLISH);
        if (delta < 0) {
            wrapper.ge(counter, 1);
        }
        return articleMapper.update(null, wrapper);
    }

    /** 发失败只记日志：写已生效，抛出去会变成「接口 500 但点赞其实成功了」 */
    private void sendLikeEvent(Long articleId, Long userId, Long authorId) {
        try {
            eventPublisher.send(new ArticleLikedEvent(ArticleLikedPayload.builder()
                    .articleId(articleId)
                    .userId(userId)
                    .authorId(authorId)
                    .build()));
        } catch (Exception e) {
            log.error("文章点赞事件发送失败, articleId={}, userId={}", articleId, userId, e);
        }
    }

    /** 画像采集失败只记日志，不影响点赞 / 收藏结果 */
    private void sendBehaviorEvent(Long articleId, Long userId, String behaviorType) {
        try {
            List<Long> tagIds = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                            .eq(ArticleTag::getArticleId, articleId))
                    .stream().map(ArticleTag::getTagId).toList();
            eventPublisher.send(new BehaviorRecordedEvent(BehaviorRecordedPayload.builder()
                    .userId(userId).articleId(articleId).behaviorType(behaviorType).tagIds(tagIds).build()));
        } catch (Exception e) {
            log.error("行为事件发送失败, articleId={}, userId={}, type={}", articleId, userId, behaviorType, e);
        }
    }

    /** 成功路径读回最新计数。计数列都是 NOT NULL DEFAULT 0，判空只防「事务内被并发硬删」这种不该发生的情况 */
    private int readCount(Long articleId, SFunction<Article, Integer> counter) {
        Article latest = articleMapper.selectById(articleId);
        if (latest == null) {
            return 0;
        }
        Integer count = counter.apply(latest);
        return count == null ? 0 : count;
    }
}
