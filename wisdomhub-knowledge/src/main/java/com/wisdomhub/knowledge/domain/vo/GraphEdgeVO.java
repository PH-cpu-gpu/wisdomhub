package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图谱可视化的一条边，方向与库里一致（画的时候可以不分方向，数据不能丢） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdgeVO {

    private String source;

    private String target;

    /** 六种关系名之一：BASED_ON / USES / PREREQUISITE / SIMILAR_TO / BELONGS_TO / ABOUT */
    private String relation;
}
