package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 编辑器源数据。草稿(1)与已发布(2)/下架(3)共用；前端据 state 决定是否开自动保存（只有 1 开） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSourceVO {

    private Long articleId;

    private String title;

    private String summary;

    /** Markdown 原文 */
    private String markdown;

    /** 1-草稿 2-已发布 3-下架 */
    private Byte state;

    private LocalDateTime updateTime;
}
