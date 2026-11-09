package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.VideoDeletedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 视频删除。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class VideoDeletedEvent extends BaseEvent<VideoDeletedPayload> {

    public VideoDeletedEvent(VideoDeletedPayload payload) {
        super(MqRoutingKeyConstants.VIDEO_DELETED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.VIDEO_DELETED;
    }
}
