package com.wisdomhub.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** ES 对账参数。Nacos 里配 wisdomhub.reconcile.* */
@Data
@Component
@ConfigurationProperties(prefix = "wisdomhub.reconcile")
public class ReconcileProperties {

    /** 增量窗口（分钟）。每次从 now-窗口 开始扫，天然带重叠；默认 30 分钟 ≈ 3 个 cron 周期 */
    private int lookbackMinutes = 30;

    /** 每页拉取的源数据条数，内容服务上限 500 */
    private int pageSize = 500;

    /** 孤儿扫描时每页读的 ES 文档数 */
    private int esScanSize = 1000;
}
