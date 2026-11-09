package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视频事件快照 = video 表那一行的元数据；标签与播放地址不进 payload，消费方回查。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoEventPayload implements Serializable {

    private Long videoId;

    private Long authorId;

    private String title;

    private String summary;

    /** 1-已发布 2-已下架 3-已删除 */
    private Integer status;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;
}
