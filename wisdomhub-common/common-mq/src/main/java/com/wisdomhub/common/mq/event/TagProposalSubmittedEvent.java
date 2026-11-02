package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.TagProposalSubmittedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 提案创建；通知服务消费后给每个管理员落一条通知并推送 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TagProposalSubmittedEvent extends BaseEvent<TagProposalSubmittedPayload> {

    public TagProposalSubmittedEvent(TagProposalSubmittedPayload payload) {
        super(MqRoutingKeyConstants.PROPOSAL_CREATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.PROPOSAL_CREATED;
    }
}
