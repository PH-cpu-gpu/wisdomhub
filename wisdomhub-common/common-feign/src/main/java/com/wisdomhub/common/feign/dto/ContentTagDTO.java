package com.wisdomhub.common.feign.dto;

import lombok.Data;

/** content-service 标签字典项，只取视频侧展示需要的字段。 */
@Data
public class ContentTagDTO {

    private Long tagId;

    private String name;

    /** 展示分组，与 content 的 TagVO.category 一致 */
    private String category;

    private String description;
}
