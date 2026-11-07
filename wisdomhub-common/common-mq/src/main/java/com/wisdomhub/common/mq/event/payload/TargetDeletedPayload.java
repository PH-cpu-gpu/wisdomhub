package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 互动目标删除：interaction 据此软删评论、清点赞收藏与计数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetDeletedPayload implements Serializable {

    private Integer targetType;

    private Long targetId;
}
