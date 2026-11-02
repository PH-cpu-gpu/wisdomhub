package com.wisdomhub.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** content-service 的标签关系快照；端点 ID 为字符串，与 Neo4j 键一致 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagRelationInternalDTO {

    private String relationId;

    private String fromTagId;

    private String toTagId;

    private String relationType;

    private Integer source;

    private Long proposalId;
}
