package com.wisdomhub.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** content-service 标签字典项；tagId 为字符串，与 Neo4j 键一致 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagInternalDTO {

    private String tagId;

    private String name;

    private String category;

    private String description;
}
