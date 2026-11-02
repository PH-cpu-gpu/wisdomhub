package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 提案提交事件；通知服务只关心 proposalId，其余字段用于日志与后续扩展 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagProposalSubmittedPayload implements Serializable {

    private Long proposalId;

    private Integer proposalType;

    private String targetKey;

    private Integer proposerType;
}
