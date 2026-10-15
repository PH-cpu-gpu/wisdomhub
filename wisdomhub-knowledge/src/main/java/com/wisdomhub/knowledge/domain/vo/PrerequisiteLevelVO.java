package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 前置链的一层，depth 从 1 开始 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrerequisiteLevelVO {

    private int depth;

    private List<TagBriefVO> tags;
}
