package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
