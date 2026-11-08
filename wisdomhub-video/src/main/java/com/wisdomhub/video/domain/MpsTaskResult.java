package com.wisdomhub.video.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpsTaskResult {

    private boolean finished;

    private boolean success;

    private String failReason;

    private Long durationMs;

    private Integer width;

    private Integer height;

    private String playlistKey;

    private String coverKey;
}
