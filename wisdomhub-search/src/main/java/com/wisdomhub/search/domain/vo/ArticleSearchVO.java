package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文章搜索结果项。只给 authorId，昵称头像由前端批量调 user-service（同 ArticleListItemVO 的约定）。
 * <p>
 * {@code highlight} 里的值是**已转义、可安全 v-html 的片段**（ES 侧开了 encoder: html）；
 * 没命中的字段不出现在这个 map 里，前端用 {@code highlight.title ?? title} 取值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSearchVO {

    private Long articleId;

    private Long authorId;

    private String title;

    private String summary;

    /** key 取 title / summary / content，value 是带 <em> 的片段 */
    private Map<String, String> highlight;

    private List<TagBriefVO> tags;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
