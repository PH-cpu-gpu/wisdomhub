package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章事件的内容快照 = `article` 表那一行的字段。
 * 正文与标签都不进 MQ：前者是几十 KB 的大字段、后者是跨表 join 的结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleEventPayload implements Serializable {

    private Long articleId;

    private Long authorId;

    private String title;

    private String summary;

    private Integer state;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;
}
