package com.wisdomhub.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.domain.document.ArticleContent;
import com.wisdomhub.content.domain.dto.ArticlePublishDTO;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.vo.ArticleBriefVO;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.repository.ArticleContentRepository;
import com.wisdomhub.content.service.ArticleService;
import com.wisdomhub.content.util.MarkdownRenderer;
import com.wisdomhub.content.util.WordCountUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    /** 摘要留空时从正文纯文本截取的长度 */
    private static final int SUMMARY_MAX = 100;

    private final ArticleMapper articleMapper;
    private final ArticleContentRepository articleContentRepository;
    private final MarkdownRenderer markdownRenderer;

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

    @Override
    public Long publish(ArticlePublishDTO dto) {
        Long authorId = CurrentUserUtil.id();
        String html = markdownRenderer.render(dto.getMarkdown());
        int wordCount = WordCountUtil.count(markdownRenderer.toPlainText(html));
        if (dto.getArticleId() == null) {
            return createArticle(dto, authorId, html, wordCount);
        }
        return overwriteArticle(dto, html, wordCount);
    }

    /**
     * 新文章。ID 必须在应用侧先算出来，否则"先写正文"这个顺序不成立。
     */
    private Long createArticle(ArticlePublishDTO dto, Long authorId, String html, int wordCount) {
        long articleId = IdWorker.getId();
        writeBody(articleId, dto, html, wordCount, null);
        try {
            articleMapper.insert(Article.builder()
                    .id(articleId)
                    .authorId(authorId)
                    .title(dto.getTitle())
                    .summary(summaryOf(dto, html))
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

    /** 覆盖已存在的文章。 */
    private Long overwriteArticle(ArticlePublishDTO dto, String html, int wordCount) {
        Long articleId = dto.getArticleId();
        Article existing = articleMapper.selectById(articleId);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        // 属主校验：绝不能拿请求体里的 articleId 直接覆盖别人的文章
        OwnerUtil.assertOwner(existing.getAuthorId());
        ArticleContent previous = articleContentRepository.findById(articleId).orElse(null);
        writeBody(articleId, dto, html, wordCount, previous);
        try {
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .set(Article::getTitle, dto.getTitle())
                    .set(Article::getSummary, summaryOf(dto, html))
                    .set(Article::getState, ArticleConstants.PUBLISH.byteValue())
                    // 首次发布才写发布时间
                    .set(Article::getPublishTime,
                            existing.getPublishTime() == null ? LocalDateTime.now(ZoneOffset.UTC) : existing.getPublishTime())
                    // update(null, wrapper) 不会触发自动填充，内容编辑要自己补 update_time
                    .set(Article::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                    .eq(Article::getId, articleId));
        } catch (RuntimeException e) {
            if (previous == null) {
                articleContentRepository.deleteById(articleId);
            } else {
                articleContentRepository.save(previous);
            }
            throw e;
        }
        return articleId;
    }

    /** 先写正文（MongoDB）。previous 非空表示是覆盖，保留原本的首次创建时间 */
    private void writeBody(long articleId, ArticlePublishDTO dto, String html, int wordCount, ArticleContent previous) {
        Instant now = Instant.now();
        articleContentRepository.save(ArticleContent.builder()
                .articleId(articleId)
                .markdown(dto.getMarkdown())
                .html(html)
                .wordCount(wordCount)
                .createTime(previous == null ? now : previous.getCreateTime())
                .updateTime(now)
                .build());
    }

    private String summaryOf(ArticlePublishDTO dto, String html) {
        if (StrUtil.isNotBlank(dto.getSummary())) {
            return dto.getSummary();
        }
        String text = markdownRenderer.toPlainText(html);
        return text.length() <= SUMMARY_MAX ? text : text.substring(0, SUMMARY_MAX);
    }
}
