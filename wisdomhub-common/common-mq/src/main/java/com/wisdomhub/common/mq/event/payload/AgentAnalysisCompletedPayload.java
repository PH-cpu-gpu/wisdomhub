package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 文章分析完成：authorId 是通知接收者。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAnalysisCompletedPayload implements Serializable {

    private Long articleId;

    private Long authorId;
}
