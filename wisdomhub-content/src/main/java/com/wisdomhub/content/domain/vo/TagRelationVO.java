package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagRelationVO {

    private Long id;

    private Long fromTagId;

    private String fromName;

    private Long toTagId;

    private String toName;

    private String relationType;

    /** 1=种子 2=管理员 3=AI 审批 */
    private Integer source;

    private Long proposalId;

    private LocalDateTime createTime;
}
