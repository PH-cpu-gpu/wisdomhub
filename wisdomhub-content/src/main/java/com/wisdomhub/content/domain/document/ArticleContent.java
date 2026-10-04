package com.wisdomhub.content.domain.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "article_content")
public class ArticleContent {

    /** 与 MySQL article.id 同值。Spring Data 不会为 Long 型 @Id 自动生成，插入前必须显式赋值 */
    @Id
    private Long articleId;

    private String markdown;

    private String html;

    private Integer wordCount;

    private Instant createTime;

    private Instant updateTime;
}
