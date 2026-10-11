package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 标签改名 / 改分组。<b>文章行不会随之变动、也不发 article.updated</b>，
 * 所以 article_index 里那份标签名只能由消费方用 update_by_query 原地改。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TagUpdatedEvent extends BaseEvent<TagEventPayload> {

    public TagUpdatedEvent(TagEventPayload payload) {
        super(MqRoutingKeyConstants.TAG_UPDATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.TAG_UPDATED;
    }
}
