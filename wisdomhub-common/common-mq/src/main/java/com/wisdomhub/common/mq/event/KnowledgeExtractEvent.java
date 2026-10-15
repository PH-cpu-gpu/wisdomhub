package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.KnowledgeExtractPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Agent 抽取结果。Python 侧产出，knowledge-service 消费。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class KnowledgeExtractEvent extends BaseEvent<KnowledgeExtractPayload> {

    public KnowledgeExtractEvent(KnowledgeExtractPayload payload) {
        super(MqRoutingKeyConstants.KNOWLEDGE_EXTRACT, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.KNOWLEDGE_EXTRACT;
    }
}
