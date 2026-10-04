package com.wisdomhub.content.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "article_content")
public class ArticleContent {
    @Id
    private Long articleId;
    private String markdown;
    private String html;
    private Integer wordCount;
    private Instant createTime;
    private Instant updateTime;
}
