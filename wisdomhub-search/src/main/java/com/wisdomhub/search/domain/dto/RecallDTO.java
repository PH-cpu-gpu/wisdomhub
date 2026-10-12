package com.wisdomhub.search.domain.dto;

import lombok.Data;

import java.util.List;

/** 按标签 / 关键词召回候选文章。keyword 与 tagIds 都可空，全空等于按热度召回 */
@Data
public class RecallDTO {

    private String keyword;

    private List<Long> tagIds;

    /** 已经推给过用户的文章，调用方传进来去重 */
    private List<Long> excludeIds;

    /** 缺省 20，上限 50 */
    private Integer limit;
}
