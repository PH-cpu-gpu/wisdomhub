package com.wisdomhub.search.domain.dto;

import lombok.Data;

import java.util.List;

/** 热门内容召回：按索引里的 viewCount 倒序，tagId 可空表示全站 */
@Data
public class HotDTO {

    private Long tagId;

    private List<Long> excludeIds;

    /** 缺省 20，上限 50 */
    private Integer limit;
}
