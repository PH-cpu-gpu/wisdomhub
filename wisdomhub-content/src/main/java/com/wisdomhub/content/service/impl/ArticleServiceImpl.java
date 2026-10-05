package com.wisdomhub.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.ArticleRedisKeyConstants;
import com.wisdomhub.content.domain.document.ArticleContent;
import com.wisdomhub.content.domain.dto.ArticleDraftDTO;
import com.wisdomhub.content.domain.dto.ArticlePublishDTO;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.vo.ArticleBriefVO;
import com.wisdomhub.content.domain.vo.ArticleDetailVO;
import com.wisdomhub.content.domain.vo.ArticleListItemVO;
import com.wisdomhub.content.domain.vo.ArticleSourceVO;
import com.wisdomhub.content.domain.vo.DraftBriefVO;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.repository.ArticleContentRepository;
import com.wisdomhub.content.service.ArticleService;
import com.wisdomhub.content.util.MarkdownRenderer;
import com.wisdomhub.content.util.WordCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    /** 摘要留空时从正文纯文本截取的长度 */
    private static final int SUMMARY_MAX = 100;

    /** sort 白名单枚举*/
    private static final String SORT_NEW = "new";
    private static final String SORT_HOT = "hot";

    private final ArticleMapper articleMapper;
    private final ArticleContentRepository articleContentRepository;
    private final MarkdownRenderer markdownRenderer;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 根据用户Id查询其已发布未删除的文章数量
     */
    @Override
    public Long countArticleByUserId(Long authorId) {
        return articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getState, ArticleConstants.PUBLISH)
                .eq(Article::getDeleted, ArticleConstants.NOT_DELETED));
    }

    @Override
    public List<ArticleBriefVO> listBrief(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .select(Article::getId, Article::getTitle, Article::getSummary)
                        .in(Article::getId, articleIds)
                        .eq(Article::getState, ArticleConstants.PUBLISH)
                        .eq(Article::getDeleted, ArticleConstants.NOT_DELETED))
                .stream()
                .map(article -> ArticleBriefVO.builder()
                        .articleId(article.getId())
                        .title(article.getTitle())
                        .summary(article.getSummary())
                        .build())
                .toList();
    }

    // ==================== 文章：公开列表 ====================

    @Override
    public PageResponse<ArticleListItemVO> listArticles(Integer page, Integer pageSize, String sort,
                                                        Long tagId, Long authorId) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getAuthorId, Article::getTitle, Article::getSummary,
                        Article::getPublishTime, Article::getViewCount, Article::getLikeCount,
                        Article::getCommentCount, Article::getFavoriteCount)
                // 对外可见：已发布 + 未删除。
                .eq(Article::getState, ArticleConstants.PUBLISH)
                .eq(Article::getDeleted, ArticleConstants.NOT_DELETED)
                // 可选筛选：条件为 false 时整条不拼进 SQL
                .eq(authorId != null, Article::getAuthorId, authorId)
                .inSql(tagId != null, Article::getId,
                        "SELECT article_id FROM article_tag WHERE tag_id = " + tagId);
        applySort(wrapper, sort);

        Page<Article> result = articleMapper.selectPage(PageUtil.of(page, pageSize), wrapper);
        List<ArticleListItemVO> items = result.getRecords().stream()
                .map(this::toListItem)
                .toList();
        return PageUtil.toResponse(result, items);
    }

    /**
     * sort 白名单映射只写在这里，绝不把前端传的字符串拼进 SQL：
     * 真正决定 SQL 的是下面两个编译期 Lambda，用户串只参与 switch 比较。
     */
    private void applySort(LambdaQueryWrapper<Article> wrapper, String sort) {
        String normalized = StrUtil.isBlank(sort) ? SORT_NEW : sort.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            // 最新：publish_time 是秒精度 DATETIME，会重复，必须用 id 兜底否则翻页会漂移
            case SORT_NEW -> wrapper.orderByDesc(Article::getPublishTime).orderByDesc(Article::getId);
            // 最热：view_count 是 MySQL 快照，最多滞后 2 分钟
            case SORT_HOT -> wrapper.orderByDesc(Article::getViewCount).orderByDesc(Article::getId);
            // 白名单外一律 400：枚举没有「最接近的合法值」，静默回退会掩盖前端拼错
            default -> throw new BusinessException(CommonErrorCode.BAD_REQUEST, "排序方式不合法");
        }
    }

    private ArticleListItemVO toListItem(Article article) {
        return ArticleListItemVO.builder()
                .articleId(article.getId())
                .authorId(article.getAuthorId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .publishTime(article.getPublishTime())
                .viewCount(article.getViewCount())
                .likeCount(article.getLikeCount())
                .commentCount(article.getCommentCount())
                .favoriteCount(article.getFavoriteCount())
                .build();
    }

    // ==================== 文章：发布 ====================

    @Override
    public Long publish(ArticlePublishDTO dto) {
        Long authorId = CurrentUserUtil.id();
        String html = markdownRenderer.render(dto.getMarkdown());
        int wordCount = WordCountUtil.count(markdownRenderer.toPlainText(html));
        // 带了 articleId：覆盖已存在文章（草稿转发布走这条）；没带：全新发布
        if (dto.getArticleId() == null) {
            return createArticle(authorId, dto.getTitle(), dto.getSummary(), dto.getMarkdown(), html, wordCount);
        }
        return overwriteArticle(dto.getArticleId(), dto.getTitle(), dto.getSummary(), dto.getMarkdown(), html, wordCount);
    }

    /**
     * 新建并发布。ID 必须在应用侧先算出来，否则"先写正文"这个顺序不成立。
     */
    private Long createArticle(Long authorId, String title, String summary, String markdown, String html, int wordCount) {
        long articleId = IdWorker.getId();
        writeBody(articleId, markdown, html, wordCount, null, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            articleMapper.insert(Article.builder()
                    .id(articleId)
                    .authorId(authorId)
                    .title(title)
                    .summary(summaryOf(summary, html))
                    .state(ArticleConstants.PUBLISH.byteValue())
                    .publishTime(LocalDateTime.now(ZoneOffset.UTC))
                    .build());
        } catch (RuntimeException e) {
            // 补偿：元数据没落成, 刚写的正文就是孤儿, 需要删掉它
            articleContentRepository.deleteById(articleId);
            throw e;
        }
        return articleId;
    }

    /**
     * 覆盖并发布已存在的文章（含草稿转发布）。
     */
    private Long overwriteArticle(Long articleId, String title, String summary, String markdown, String html, int wordCount) {
        Article existing = articleMapper.selectById(articleId);
        if (existing == null || existing.getDeleted() == ArticleConstants.DELETED.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        // 属主校验：绝不能拿请求体里的 articleId 直接覆盖别人的文章
        OwnerUtil.assertOwner(existing.getAuthorId());

        ArticleContent previous = articleContentRepository.findById(articleId).orElse(null);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        writeBody(articleId, markdown, html, wordCount, previous, now);

        int affected;
        try {
            affected = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .set(Article::getTitle, title)
                    .set(Article::getSummary, summaryOf(summary, html))
                    .set(Article::getState, ArticleConstants.PUBLISH.byteValue())
                    // 首次发布才写发布时间
                    .set(Article::getPublishTime, existing.getPublishTime() == null
                            ? LocalDateTime.now(ZoneOffset.UTC) : existing.getPublishTime())
                    // update(null, wrapper) 不会触发自动填充，内容编辑要自己补 update_time
                    .set(Article::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                    .eq(Article::getId, articleId)
                    .eq(Article::getDeleted, ArticleConstants.NOT_DELETED));
        } catch (RuntimeException e) {
            restoreBody(articleId, previous, now);
            throw e;
        }
        if (affected == 0) {
            restoreBody(articleId, previous, now);
            throw new BusinessException(CommonErrorCode.CONFLICT, "文章状态已变化，发布未生效");
        }
        return articleId;
    }

    // ==================== 文章：草稿 ====================

    @Override
    public Long createDraft(ArticleDraftDTO dto) {
        Long authorId = CurrentUserUtil.id();
        String markdown = StrUtil.nullToEmpty(dto.getMarkdown());
        String html = markdownRenderer.render(markdown);
        int wordCount = WordCountUtil.count(markdownRenderer.toPlainText(html));
        return createDraftArticle(authorId, StrUtil.nullToEmpty(dto.getTitle()), markdown, html, wordCount);
    }

    /**
     * 新建草稿。
     */
    private Long createDraftArticle(Long authorId, String title, String markdown, String html, int wordCount) {
        long articleId = IdWorker.getId();
        writeBody(articleId, markdown, html, wordCount, null, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            articleMapper.insert(Article.builder()
                    .id(articleId)
                    .authorId(authorId)
                    .title(title)
                    // 草稿不算摘要，发布时按最终正文重算
                    .summary("")
                    .state(ArticleConstants.DRAFT.byteValue())
                    .build());
        } catch (RuntimeException e) {
            // 补偿：元数据没落成, 刚写的正文就是孤儿, 需要删掉它
            articleContentRepository.deleteById(articleId);
            throw e;
        }
        return articleId;
    }

    @Override
    public Long updateDraft(Long articleId, ArticleDraftDTO dto) {
        String markdown = StrUtil.nullToEmpty(dto.getMarkdown());
        String html = markdownRenderer.render(markdown);
        int wordCount = WordCountUtil.count(markdownRenderer.toPlainText(html));
        return updateDraftArticle(articleId, StrUtil.nullToEmpty(dto.getTitle()), markdown, html, wordCount);
    }

    /**
     * 更新草稿。
     */
    private Long updateDraftArticle(Long articleId, String title, String markdown, String html, int wordCount) {
        Article existing = articleMapper.selectById(articleId);
        if (existing == null || existing.getDeleted() == ArticleConstants.DELETED.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "草稿不存在");
        }
        OwnerUtil.assertOwner(existing.getAuthorId());
        // 自动保存只对草稿生效，已发布/下架不能被 autosave 污染线上
        if (existing.getState() != ArticleConstants.DRAFT.byteValue()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该文章不是草稿，已停止自动保存");
        }

        ArticleContent previous = articleContentRepository.findById(articleId).orElse(null);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        writeBody(articleId, markdown, html, wordCount, previous, now);

        int affected;
        try {
            affected = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .set(Article::getTitle, title)
                    // update(null, wrapper) 不会触发自动填充，内容编辑要自己补 update_time
                    .set(Article::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                    .eq(Article::getId, articleId)
                    .eq(Article::getDeleted, ArticleConstants.NOT_DELETED)
                    // 并发发布后这次 autosave 影响 0 行，不覆盖线上
                    .eq(Article::getState, ArticleConstants.DRAFT));
        } catch (RuntimeException e) {
            restoreBody(articleId, previous, now);
            throw e;
        }
        if (affected == 0) {
            restoreBody(articleId, previous, now);
            throw new BusinessException(CommonErrorCode.CONFLICT, "草稿状态已变化，保存未生效");
        }
        return articleId;
    }

    @Override
    public void deleteDraft(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || article.getDeleted() == ArticleConstants.DELETED.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "草稿不存在");
        }
        OwnerUtil.assertOwner(article.getAuthorId());
        if (article.getState() != ArticleConstants.DRAFT.byteValue()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "只能删除草稿");
        }
        // 与新建/发布相反：先让 MySQL 行不可见，再删正文。最坏是看不见的孤儿正文
        int affected = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .set(Article::getDeleted, ArticleConstants.DELETED.byteValue())
                .set(Article::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Article::getId, articleId)
                .eq(Article::getDeleted, ArticleConstants.NOT_DELETED)
                .eq(Article::getState, ArticleConstants.DRAFT));
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "草稿状态已变化，删除未生效");
        }
        try {
            articleContentRepository.deleteById(articleId);
        } catch (RuntimeException e) {
            // 行已不可见，正文成孤儿；不抛，免得用户以为没删掉
            log.error("草稿正文删除失败, 已成孤儿待清理, articleId={}", articleId, e);
        }
    }

    @Override
    public PageResponse<DraftBriefVO> listDrafts(Integer page, Integer pageSize) {
        Page<Article> result = articleMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Article>()
                        .select(Article::getId, Article::getTitle, Article::getUpdateTime)
                        .eq(Article::getAuthorId, CurrentUserUtil.id())
                        .eq(Article::getState, ArticleConstants.DRAFT)
                        .eq(Article::getDeleted, ArticleConstants.NOT_DELETED)
                        .orderByDesc(Article::getUpdateTime));
        List<DraftBriefVO> items = result.getRecords().stream()
                .map(article -> DraftBriefVO.builder()
                        .articleId(article.getId())
                        .title(article.getTitle())
                        .updateTime(article.getUpdateTime())
                        .build())
                .toList();
        return PageUtil.toResponse(result, items);
    }

    @Override
    public ArticleSourceVO getSource(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || article.getDeleted() == ArticleConstants.DELETED.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        OwnerUtil.assertOwner(article.getAuthorId());
        ArticleContent content = articleContentRepository.findById(articleId).orElse(null);
        if (content == null) {
            // 不变式被破坏：行可见却没有正文。属服务端数据问题，不能当空文章返回
            log.error("文章正文缺失, articleId={}", articleId);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "文章正文缺失");
        }
        return ArticleSourceVO.builder()
                .articleId(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .markdown(content.getMarkdown())
                .state(article.getState())
                .updateTime(article.getUpdateTime())
                .build();
    }

    // ==================== 文章：详情 ====================

    @Override
    public ArticleDetailVO getDetail(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        // 只有已发布且未删除对外可见；草稿/下架/已删一律 404，作者本人也不例外（草稿走 /source）
        if (article == null
                || article.getDeleted() == ArticleConstants.DELETED.byteValue()
                || article.getState() != ArticleConstants.PUBLISH.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        ArticleContent content = articleContentRepository.findById(articleId).orElse(null);
        if (content == null) {
            // 不变式被破坏：行可见却没有正文。属服务端数据问题，不能当空文章返回
            log.error("文章正文缺失, articleId={}", articleId);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "文章正文缺失");
        }
        return ArticleDetailVO.builder()
                .articleId(article.getId())
                .authorId(article.getAuthorId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .html(content.getHtml())
                .publishTime(article.getPublishTime())
                // 计数放最后：前面的 404 / 500 都不该污染阅读量
                .viewCount(resolveViewCount(article))
                .likeCount(article.getLikeCount())
                .commentCount(article.getCommentCount())
                .favoriteCount(article.getFavoriteCount())
                .build();
    }

    /** 阅读量：Redis 为读源，登录用户按 RedisTtl.VIEW_ONCE 窗口去重，游客只读不计数 */
    private int resolveViewCount(Article article) {
        String countKey = ArticleRedisKeyConstants.VIEW_COUNT_KEY + article.getId();
        try {
            int current = ensureCount(countKey, () -> (long) snapshotViewCount(article));
            Long userId = UserContext.getUserId();
            if (userId == null) {
                // 游客没有 userId，去重无从谈起，索性不计
                return current;
            }
            // 使用SETNX, 只有一个线程能重建成功
            Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                    ArticleRedisKeyConstants.VIEW_ONCE_KEY + userId + ":" + article.getId(),
                    "1", RedisTtl.VIEW_ONCE);
            if (!Boolean.TRUE.equals(first)) {
                return current;
            }
            Long latest = stringRedisTemplate.opsForValue().increment(countKey);
            // 真正计数的那次返回 INCR 之后的值，让用户看到自己 +1
            return latest == null ? current : Math.toIntExact(latest);
        } catch (Exception e) {
            // Redis 不可用：退回 article.view_count 快照列，详情页不因 Redis 故障而挂
            log.warn("阅读量读取失败, 退回 article.view_count 快照: articleId={}", article.getId(), e);
            return snapshotViewCount(article);
        }
    }

    private int snapshotViewCount(Article article) {
        return article.getViewCount() == null ? 0 : article.getViewCount();
    }

    /** 确保计数 key 存在，miss 则用快照重建。SETNX 回填，避免覆盖并发 INCR */
    private int ensureCount(String key, Supplier<Long> dbCount) {
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Integer.parseInt(cached);
        }
        int count = Math.toIntExact(dbCount.get());
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(count));
        return count;
    }

    // ==================== 双写与补偿（四条路径共用） ====================

    /** 写正文（MongoDB）。previous 非空表示是覆盖，保留原本的首次创建时间 */
    private void writeBody(long articleId, String markdown, String html, int wordCount,
                           ArticleContent previous, Instant now) {
        articleContentRepository.save(ArticleContent.builder()
                .articleId(articleId)
                .markdown(markdown)
                .html(html)
                .wordCount(wordCount)
                .createTime(previous == null ? now : previous.getCreateTime())
                .updateTime(now)
                .build());
    }

    /** 回滚正文，但只在它还停留在本次写入的那一版时（被并发保存覆盖了就不能拿旧数据倒退） */
    private void restoreBody(long articleId, ArticleContent previous, Instant writtenAt) {
        ArticleContent current = articleContentRepository.findById(articleId).orElse(null);
        if (current == null || !writtenAt.equals(current.getUpdateTime())) {
            return;
        }
        if (previous == null) {
            articleContentRepository.deleteById(articleId);
        } else {
            articleContentRepository.save(previous);
        }
    }

    private String summaryOf(String summary, String html) {
        if (StrUtil.isNotBlank(summary)) {
            return summary;
        }
        String text = markdownRenderer.toPlainText(html);
        return text.length() <= SUMMARY_MAX ? text : text.substring(0, SUMMARY_MAX);
    }
}
