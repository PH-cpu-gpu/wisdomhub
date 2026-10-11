package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 标签字典项的快照，四个字段都在 tag 表里 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagEventPayload implements Serializable {

    private Long tagId;

    private String name;

    /** 展示分组 */
    private String category;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
