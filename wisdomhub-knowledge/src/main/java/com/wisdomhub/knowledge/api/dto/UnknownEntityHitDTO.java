package com.wisdomhub.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 未收录实体上报：文章抽取时被丢弃的实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnknownEntityHitDTO {

    private Long articleId;

    private List<String> names;
}
