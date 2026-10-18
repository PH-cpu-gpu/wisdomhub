package com.wisdomhub.recommendation.api.search.dto;

import lombok.Data;

import java.util.List;

/** 对应 search 的 RecallDTO；keyword 与 tagIds 全空 = 按热度召回 */
@Data
public class RecallRequest {

    private String keyword;

    private List<Long> tagIds;

    private List<Long> excludeIds;

    /** 缺省 20，上限 50 */
    private Integer limit;
}
