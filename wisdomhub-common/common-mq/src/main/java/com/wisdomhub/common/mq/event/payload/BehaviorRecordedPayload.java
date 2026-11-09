package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** 一次用户行为。文章/视频行为带对应 id + tagIds；搜索带 keyword，tagId 为可选的过滤条件 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorRecordedPayload implements Serializable {

    private Long userId;

    private String behaviorType;

    private Long articleId;

    /** 视频行为用；文章事件保持 articleId 不变 */
    private Long videoId;

    private List<Long> tagIds;

    private String keyword;

    private Long tagId;
}
