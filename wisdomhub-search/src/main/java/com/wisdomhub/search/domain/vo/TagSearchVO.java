package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** 标签搜索结果项 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagSearchVO {

    private Long tagId;

    private String name;

    /** 展示分组，与图谱 Technology.category 无关 */
    private String category;

    private String description;

    /** key 取 name / description */
    private Map<String, String> highlight;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
