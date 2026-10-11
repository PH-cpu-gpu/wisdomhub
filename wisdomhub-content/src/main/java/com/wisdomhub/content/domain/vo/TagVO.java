package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 标签字典项。作为两级缓存的缓存值，必须是普通类（record 是隐式 final，default typing 不写 @class） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagVO {

    private Long tagId;

    private String name;

    /** 展示分组，前端按它给标签云分组 */
    private String category;

    private String description;
}
