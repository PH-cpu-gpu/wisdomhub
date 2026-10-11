package com.wisdomhub.search.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对应 content-service 的 ArticleInternalVO（本服务只消费，不反向定义契约，同 user-service 的 ArticleBriefDTO）。
 * 正文与标签不在 MQ payload 里，只能回查，所以索引侧必须走这个接口。
 */
@Data
public class ArticleInternalDTO {

    private Long articleId;

    private Long authorId;

    private String title;

    private String summary;

    /** 1-草稿 2-已发布 3-下架；只有 2 该进索引 */
    private Integer state;

    private LocalDateTime createTime;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;

    private List<ArticleTagDTO> tags;

    private String markdown;
}
