package com.wisdomhub.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 未收录实体升级成提案的门槛与批量。只按不同文章数判断，查询次数只展示。 */
@Data
@Component
@ConfigurationProperties(prefix = "wisdomhub.tag.promote")
public class TagPromoteProperties {

    /** 至少被多少篇不同文章抽取命中才升级 */
    private int minArticles = 3;

    /** 单次任务最多处理多少条候选，避免一轮给管理员推太多提案 */
    private int batchSize = 50;
}
