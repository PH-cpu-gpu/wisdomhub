package com.wisdomhub.recommendation.api.search.dto;

import lombok.Data;

import java.util.List;

/** 对应 search 的 HotDTO；tagId 空 = 全站 */
@Data
public class HotRequest {

    private Long tagId;

    private List<Long> excludeIds;

    /** 缺省 20，上限 50 */
    private Integer limit;
}
