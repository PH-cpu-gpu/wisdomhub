package com.wisdomhub.search.domain.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * article_index 的写入文档，字段与 resources/es/article_index.json 逐一对齐。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArticleIndexDoc {

    private Long articleId;

    private String title;

    /** completion 字段，输入建议用。*/
    private String suggest;

    private String summary;

    /** markdown 原文 */
    private String content;

    private Long authorId;

    private List<Tag> tags;

    private Integer state;

    private LocalDateTime createTime;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tag {

        private Long tagId;

        private String name;

        private String category;
    }
}
