package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 命中文章自带的标签，供结果列表展示与筛选回显 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagBriefVO {

    private Long tagId;

    private String name;

    private String category;
}
