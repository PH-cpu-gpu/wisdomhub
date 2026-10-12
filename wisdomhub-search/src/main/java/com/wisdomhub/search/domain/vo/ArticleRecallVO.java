package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 召回候选文章。比 ArticleSearchVO 瘦：无高亮、无正文，
 * 带上打分要用的标签 / 计数 / 发布时间，省得推荐侧再逐篇回调 content-service。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRecallVO {

    private Long articleId;

    private Long authorId;

    private String title;

    private String summary;

    private List<TagBriefVO> tags;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
