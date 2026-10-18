package com.wisdomhub.recommendation.api.search.dto;

import lombok.Data;

import java.util.List;

/** 对应 search 的 SimilarDTO */
@Data
public class SimilarRequest {

    private Long articleId;

    private List<Long> excludeIds;

    /** 缺省 20，上限 50 */
    private Integer limit;
}
