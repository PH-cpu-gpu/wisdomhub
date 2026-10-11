package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务间读契约：一篇「文章的完整快照」= article 行 + 标签 + MongoDB 正文。
 * 消费方是 search-service。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleInternalVO {

    private Long articleId;

    private Long authorId;

    private String title;

    private String summary;

    /** 1-草稿 2-已发布 3-下架。只有 2 该进索引；1/3 由消费方据此从索引里删掉 */
    private Integer state;

    private LocalDateTime createTime;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;

    private List<ArticleTagVO> tags;

    /** markdown 原文，来自 MongoDB。正文缺失时单篇接口报错、批量接口跳过该行 */
    private String markdown;
}
