package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 公开文章列表项。只承载 MySQL 元数据：不返回正文（正文在 MongoDB），
 * 不返回标签（标签归 B1.24），state 恒为「已发布」故不返回。
 * authorId 只给 ID，昵称头像由前端批量调 user-service。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleListItemVO {

    private Long articleId;

    private Long authorId;

    private String title;

    private String summary;

    private LocalDateTime publishTime;

    /** 阅读量 = article.view_count 快照，由 B1.22 每 2 分钟从 Redis 落库，最热排序最多滞后 2 分钟 */
    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
