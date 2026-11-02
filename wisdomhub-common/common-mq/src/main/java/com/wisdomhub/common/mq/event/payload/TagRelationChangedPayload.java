package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 标签关系变更快照。action 区分新增与删除，消费方按它更新投影。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagRelationChangedPayload implements Serializable {

    public static final String ACTION_CREATED = "CREATED";

    public static final String ACTION_DELETED = "DELETED";

    private Long relationId;

    private Long fromTagId;

    private Long toTagId;

    private String relationType;

    private String action;

    /** 1=种子 2=管理员 3=AI 审批 */
    private Integer source;

    private Long proposalId;
}
