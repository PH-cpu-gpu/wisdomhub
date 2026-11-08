package com.wisdomhub.video.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoDetailVO {

    private Long videoId;

    private Long authorId;

    private String title;

    private String summary;

    private String coverKey;

    private String coverUrl;

    private String playlistUrl;

    private Long durationMs;

    private Integer width;

    private Integer height;

    private Integer viewCount;

    private Integer status;

    private LocalDateTime publishTime;
}
