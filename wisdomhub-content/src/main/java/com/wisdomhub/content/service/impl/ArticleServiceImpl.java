package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.vo.ArticleBriefVO;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;

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
}
