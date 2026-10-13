package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 图谱种子导入结果 */
@Data
@AllArgsConstructor
public class SeedResultVO {

    private int tagsLoaded;

    private int edgesSeeded;

    private int edgesDropped;
}
