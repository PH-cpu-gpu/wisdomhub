package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 全量关系快照，供 knowledge 建图与对账；ID 用字符串，与 Neo4j 键一致 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagRelationSyncVO {

    private String relationId;

    private String fromTagId;

    private String toTagId;

    private String relationType;

    private Integer source;

    private Long proposalId;
}
