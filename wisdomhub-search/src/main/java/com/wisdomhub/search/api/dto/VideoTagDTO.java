package com.wisdomhub.search.api.dto;

import lombok.Data;

/** video-service 内部接口返回的标签摘要。 */
@Data
public class VideoTagDTO {

    private Long tagId;

    private String name;

    private String category;
}
