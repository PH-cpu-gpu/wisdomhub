package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.ArticleEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 文章发布。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArticleCreatedEvent extends BaseEvent<ArticleEventPayload> {

    public ArticleCreatedEvent(ArticleEventPayload payload) {
        super(MqRoutingKeyConstants.ARTICLE_CREATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.ARTICLE_CREATED;
    }
}
