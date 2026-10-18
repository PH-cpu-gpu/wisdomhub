package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** 一次用户行为。文章类行为带 articleId + tagIds；搜索带 keyword，tagId 为可选的过滤条件 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorRecordedPayload implements Serializable {

    private Long userId;

    private String behaviorType;

    private Long articleId;

    private List<Long> tagIds;

    private String keyword;

    private Long tagId;
}
