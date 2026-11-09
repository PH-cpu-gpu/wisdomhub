package com.wisdomhub.video.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 视频标签，name/category 每次从 content 标签字典取，避免改名后展示陈旧 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoTagVO {

    private Long tagId;

    private String name;

    private String category;
}
