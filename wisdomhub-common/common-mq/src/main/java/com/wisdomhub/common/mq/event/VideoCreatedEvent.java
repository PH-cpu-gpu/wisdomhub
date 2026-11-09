package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.VideoEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 视频发布。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class VideoCreatedEvent extends BaseEvent<VideoEventPayload> {

    public VideoCreatedEvent(VideoEventPayload payload) {
        super(MqRoutingKeyConstants.VIDEO_CREATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.VIDEO_CREATED;
    }
}
