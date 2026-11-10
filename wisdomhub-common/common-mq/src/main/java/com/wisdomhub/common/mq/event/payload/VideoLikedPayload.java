package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 视频点赞：authorId 是通知接收者，userId 是点赞者。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoLikedPayload implements Serializable {

    private Long videoId;

    private Long userId;

    private Long authorId;
}
