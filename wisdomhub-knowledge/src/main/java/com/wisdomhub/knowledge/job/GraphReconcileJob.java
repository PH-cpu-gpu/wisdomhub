package com.wisdomhub.knowledge.job;

import com.wisdomhub.knowledge.domain.vo.GraphReconcileResult;
import com.wisdomhub.knowledge.service.GraphReconcileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Neo4j Article 节点对账：清掉 MySQL 已删文章与无 ABOUT 边的孤岛节点。 */
@Component
@RequiredArgsConstructor
public class GraphReconcileJob {

    private final GraphReconcileService graphReconcileService;

    @XxlJob("graphReconcileHandler")
    public void reconcile() {
        try {
            GraphReconcileResult result = graphReconcileService.reconcile();
            XxlJobHelper.log("Neo4j 对账完成: " + result.summary());
            XxlJobHelper.handleSuccess(result.summary());
        } catch (Exception e) {
            XxlJobHelper.handleFail("Neo4j 对账失败: " + e.getMessage());
            throw e;
        }
    }
}
