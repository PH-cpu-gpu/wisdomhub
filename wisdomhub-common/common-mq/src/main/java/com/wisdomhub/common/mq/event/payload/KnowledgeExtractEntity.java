package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Agent 抽出的技术实体。name 是 LLM 自由文本，落库时按 name 解析到 tag 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeExtractEntity implements Serializable {

    private String name;
}
