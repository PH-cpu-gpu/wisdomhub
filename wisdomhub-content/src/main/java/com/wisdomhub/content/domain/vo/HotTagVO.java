package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热门标签项。作为两级缓存的缓存值，必须是普通类（record 是隐式 final，default typing 不写 @class） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotTagVO {

    private Long tagId;

    private String name;

    /** 该标签下「已发布且未删除」的文章数 */
    private Integer articleCount;
}
