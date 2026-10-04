package com.wisdomhub.user.api.dto;

import lombok.Data;

/** content-service 返回的文章摘要。刻意不复用 content 的 VO，两边各自演化 */
@Data
public class ArticleBriefDTO {

    private Long articleId;

    private String title;

    private String summary;
}
