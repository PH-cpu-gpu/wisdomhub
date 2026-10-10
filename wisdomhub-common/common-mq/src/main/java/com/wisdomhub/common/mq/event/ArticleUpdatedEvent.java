package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.ArticleEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 文章覆盖发布（改内容 / 换标签）。与 created 分开是为了让只在 created 动作的消费者
 * （AI 分析、图谱抽取）不被打扰 —— 改一次文章不该重新做一次 LLM 分析
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArticleUpdatedEvent extends BaseEvent<ArticleEventPayload> {

    public ArticleUpdatedEvent(ArticleEventPayload payload) {
        super(MqRoutingKeyConstants.ARTICLE_UPDATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.ARTICLE_UPDATED;
    }
}
