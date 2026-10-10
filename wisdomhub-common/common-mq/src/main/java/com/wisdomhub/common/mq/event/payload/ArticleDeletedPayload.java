package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文章删除事件只需要 id 和 作者 id。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDeletedPayload implements Serializable {

    private Long articleId;

    private Long authorId;
}
