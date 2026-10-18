package com.wisdomhub.recommendation.api.knowledge.dto;

import lombok.Data;

/** 对应 knowledge 的 TagBriefVO；tagId 是字符串，与 Neo4j 键一致 */
@Data
public class TagBriefDTO {

    private String tagId;

    private String name;
}
