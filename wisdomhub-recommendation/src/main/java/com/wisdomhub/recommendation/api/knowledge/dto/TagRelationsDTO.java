package com.wisdomhub.recommendation.api.knowledge.dto;

import lombok.Data;

import java.util.Map;

/** 对应 knowledge 的 TagRelationsVO；relations 的 key 是 Neo4j 关系名 */
@Data
public class TagRelationsDTO {

    private String tagId;

    private String name;

    private Map<String, RelationGroupDTO> relations;
}
