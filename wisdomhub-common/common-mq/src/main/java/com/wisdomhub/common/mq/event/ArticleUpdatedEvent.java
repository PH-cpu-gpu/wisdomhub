package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.ArticleEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 文章覆盖发布（改内容 / 换标签 / 将来的下架）。AI 分析也消费它，按 payload.updateTime
 * 做版本幂等：同一版本不重复调 LLM，search 对账重投的 updated 因此不会触发重分析
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
