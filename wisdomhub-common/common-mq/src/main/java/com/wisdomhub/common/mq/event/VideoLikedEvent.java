package com.wisdomhub.common.mq.event;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.VideoLikedPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 视频点赞。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class VideoLikedEvent extends BaseEvent<VideoLikedPayload> {

    public VideoLikedEvent(VideoLikedPayload payload) {
        super(MqRoutingKeyConstants.VIDEO_LIKED, payload);
    }

    @Override
    public String routingKey() {
        return MqRoutingKeyConstants.VIDEO_LIKED;
    }
}
