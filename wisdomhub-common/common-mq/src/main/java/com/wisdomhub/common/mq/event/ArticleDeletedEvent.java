package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.ArticleDeletedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 文章删除。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArticleDeletedEvent extends BaseEvent<ArticleDeletedPayload> {

    public ArticleDeletedEvent(ArticleDeletedPayload payload) {
        super(MqRoutingKeyConstants.ARTICLE_DELETED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.ARTICLE_DELETED;
    }
}
