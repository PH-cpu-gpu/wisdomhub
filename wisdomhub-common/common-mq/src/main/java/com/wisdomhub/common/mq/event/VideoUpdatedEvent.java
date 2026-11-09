package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.VideoEventPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 视频编辑 / 改标签 / 下架。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class VideoUpdatedEvent extends BaseEvent<VideoEventPayload> {

    public VideoUpdatedEvent(VideoEventPayload payload) {
        super(MqRoutingKeyConstants.VIDEO_UPDATED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.VIDEO_UPDATED;
    }
}
