package com.wisdomhub.video.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAssetStatusVO {

    private Long assetId;

    private Integer status;

    private Long fileSize;

    private Long durationMs;

    private Integer width;

    private Integer height;

    private String coverKey;

    private String failReason;
}
