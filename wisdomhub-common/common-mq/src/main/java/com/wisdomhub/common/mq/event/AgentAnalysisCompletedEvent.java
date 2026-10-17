package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.AgentAnalysisCompletedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * AI 文章分析完成。eventType 从类名压成 ai.completed，不要改成 ai.analysis.completed。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AgentAnalysisCompletedEvent extends BaseEvent<AgentAnalysisCompletedPayload> {

    public AgentAnalysisCompletedEvent(AgentAnalysisCompletedPayload payload) {
        super(MqRoutingKeyConstants.AI_COMPLETED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.AI_COMPLETED;
    }
}
