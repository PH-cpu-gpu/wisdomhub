package com.wisdomhub.search.domain.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * tag_index 的写入文档，字段与 resources/es/tag_index.json 逐一对齐。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagIndexDoc {

    private Long tagId;

    private String name;

    /** completion 字段，写入时显式带上，取 name */
    private String suggest;

    /** 展示分组，与图谱 Technology.category 无关 */
    private String category;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
