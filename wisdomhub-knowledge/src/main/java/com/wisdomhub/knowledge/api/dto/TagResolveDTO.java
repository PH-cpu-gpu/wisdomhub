package com.wisdomhub.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** content-service 的标签名解析结果；tagId 为字符串，与 Neo4j 键一致 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagResolveDTO {

    private String name;

    private String tagId;

    private String canonicalName;
}
