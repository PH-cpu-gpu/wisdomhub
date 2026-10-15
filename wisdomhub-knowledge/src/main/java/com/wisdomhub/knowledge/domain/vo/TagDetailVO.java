package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 技术详情：节点属性 + 相关技术（与 /related 返回的是同一份） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDetailVO {

    private String tagId;

    private String name;

    /** tag 表的中文分组名，前端着色按它走 */
    private String category;

    private String description;

    private Map<String, RelationGroupVO> relations;
}
