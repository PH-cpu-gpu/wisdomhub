package com.wisdomhub.content.service;

import com.wisdomhub.content.domain.dto.ArticlePublishDTO;
import com.wisdomhub.content.domain.vo.ArticleBriefVO;

import java.util.List;

public interface ArticleService {

    Long countArticleByUserId(Long authorId);

    /** 按 ID 批量取文章摘要，只返回已发布且未删除的；返回顺序不保证，由调用方按需重排 */
    List<ArticleBriefVO> listBrief(List<Long> articleIds);

    /** 发布文章：正文写 MongoDB、元数据写 MySQL。返回文章 ID */
    Long publish(ArticlePublishDTO dto);
}
