package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图谱里的技术节点摘要，只有身份与名字 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagBriefVO {

    private String tagId;

    private String name;
}
