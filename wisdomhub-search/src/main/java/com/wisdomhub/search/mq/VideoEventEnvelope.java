package com.wisdomhub.search.mq;

import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.payload.VideoEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 一个队列收 video.created / video.updated / video.deleted；删除事件的 payload 是快照的子集。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class VideoEventEnvelope extends BaseEvent<VideoEventPayload> {

    @Override
    public String routingKey() {
        return getEventType();
    }
}
