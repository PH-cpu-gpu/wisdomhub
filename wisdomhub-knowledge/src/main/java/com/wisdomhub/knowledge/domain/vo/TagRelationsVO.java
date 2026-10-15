package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 相关技术：该技术的全部直接邻居，按关系类型分组（key 就是 Neo4j 关系名） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagRelationsVO {

    private String tagId;

    private String name;

    private Map<String, RelationGroupVO> relations;
}
