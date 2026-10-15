package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 前置知识链：沿 PREREQUISITE 展开，按层返回；truncated = 到了 maxDepth 还有没展开的前置 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrerequisiteTreeVO {

    private String tagId;

    private String name;

    /** 展开到的层数，等于 levels.size() */
    private int depth;

    private List<PrerequisiteLevelVO> levels;

    private boolean truncated;
}
