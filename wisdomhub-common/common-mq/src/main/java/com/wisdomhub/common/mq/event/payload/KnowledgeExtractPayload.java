package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Agent产出, 文章抽取后产生的About关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeExtractPayload implements Serializable {

    private Long articleId;

    private List<KnowledgeExtractEntity> entities;
}
