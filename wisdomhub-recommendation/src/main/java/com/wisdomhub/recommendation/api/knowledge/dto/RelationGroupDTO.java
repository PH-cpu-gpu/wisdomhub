package com.wisdomhub.recommendation.api.knowledge.dto;

import lombok.Data;

import java.util.List;

/** 一种关系类型的邻居；out = 我指向对方，in = 对方指向我 */
@Data
public class RelationGroupDTO {

    private List<TagBriefDTO> out;

    private List<TagBriefDTO> in;
}
