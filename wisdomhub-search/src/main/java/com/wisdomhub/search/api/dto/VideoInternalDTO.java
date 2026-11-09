package com.wisdomhub.search.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 对应 video-service 的 VideoInternalVO；标签与其余字段只能回查拿。 */
@Data
public class VideoInternalDTO {

    private Long videoId;

    private Long authorId;

    private String title;

    private String summary;

    private String coverKey;

    private Long durationMs;

    private Integer width;

    private Integer height;

    /** 1-已发布 2-已下架；只有 1 该进索引 */
    private Integer status;

    private Integer viewCount;

    /** 以下三个计数不从 video-service 返回，由 interaction 回填 */
    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;

    private LocalDateTime createTime;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    private List<VideoTagDTO> tags;
}
