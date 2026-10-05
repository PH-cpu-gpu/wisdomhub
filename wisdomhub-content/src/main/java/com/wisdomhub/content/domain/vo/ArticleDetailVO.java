package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 文章详情。正文给消毒后的 html；markdown 原文只走属主私有的 /source */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailVO {

    private Long articleId;

    /** 只给 ID，昵称头像由前端另调 user-service，详情读路径不跨服务 */
    private Long authorId;

    private String title;

    private String summary;

    /** 渲染并消毒后的正文 */
    private String html;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
