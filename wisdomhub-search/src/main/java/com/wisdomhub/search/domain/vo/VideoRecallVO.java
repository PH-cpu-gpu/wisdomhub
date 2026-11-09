package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 召回候选视频，字段与 ArticleRecallVO 对齐，另带封面与时长。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoRecallVO {

    private Long videoId;

    private Long authorId;

    private String title;

    private String summary;

    private List<TagBriefVO> tags;

    private String coverKey;

    private Long durationMs;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
