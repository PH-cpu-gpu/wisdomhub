package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 一种关系类型的邻居，out = 我指向对方，in = 对方指向我 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationGroupVO {

    private List<TagBriefVO> out;

    private List<TagBriefVO> in;
}
