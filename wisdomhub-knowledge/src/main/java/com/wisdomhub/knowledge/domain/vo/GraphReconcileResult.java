package com.wisdomhub.knowledge.domain.vo;

import lombok.Data;

/** 图谱对账结果，只用于任务日志与验收。 */
@Data
public class GraphReconcileResult {

    private int tagNodes;

    private int staleTags;

    private int staleRelations;

    private int articleNodes;

    private int missingArticles;

    private int noEdgeNodes;

    public String summary() {
        return "tagNodes=" + tagNodes + ", staleTags=" + staleTags + ", staleRelations=" + staleRelations
                + ", articleNodes=" + articleNodes + ", missingArticles=" + missingArticles
                + ", noEdgeNodes=" + noEdgeNodes;
    }
}
