package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 图谱可视化数据；总览只有 Tag 节点，邻域还会挂上中心的文章 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphVO {

    private List<GraphNodeVO> nodes;

    private List<GraphEdgeVO> edges;

    /** 中心的文章超过 articleLimit 被截断（总览恒 false） */
    private boolean articlesTruncated;
}
