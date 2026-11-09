package com.wisdomhub.recommendation.api.search.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 对应 search 的 VideoRecallVO；打分要用的标签与计数都在这里。 */
@Data
public class VideoRecallDTO {

    private Long videoId;

    private Long authorId;

    private String title;

    private String summary;

    private List<TagBriefDTO> tags;

    private String coverKey;

    private Long durationMs;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
