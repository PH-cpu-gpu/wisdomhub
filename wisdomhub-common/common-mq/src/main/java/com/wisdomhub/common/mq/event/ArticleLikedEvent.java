package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.ArticleLikedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 文章点赞。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArticleLikedEvent extends BaseEvent<ArticleLikedPayload> {

    public ArticleLikedEvent(ArticleLikedPayload payload) {
        super(MqRoutingKeyConstants.ARTICLE_LIKED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.ARTICLE_LIKED;
    }
}
