package com.wisdomhub.video.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 供 search 建索引的完整快照；标签名从 content 字典取。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoInternalVO {

    private Long videoId;

    private Long authorId;

    private String title;

    private String summary;

    private String coverKey;

    private Long durationMs;

    private Integer width;

    private Integer height;

    /** 1-已发布 2-已下架 3-已删除；删除态不返回 */
    private Integer status;

    private Integer viewCount;

    private LocalDateTime createTime;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    private List<VideoTagVO> tags;
}
