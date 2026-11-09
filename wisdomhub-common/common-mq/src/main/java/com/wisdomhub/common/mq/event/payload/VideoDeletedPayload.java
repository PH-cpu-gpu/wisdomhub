package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 视频删除事件只需要 id 和作者 id。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoDeletedPayload implements Serializable {

    private Long videoId;

    private Long authorId;
}
