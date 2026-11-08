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
public class MyVideoVO {

    private Long videoId;

    private String title;

    private String summary;

    private String coverKey;

    private String coverUrl;

    private Long durationMs;

    private Integer viewCount;

    private Integer status;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;
}
