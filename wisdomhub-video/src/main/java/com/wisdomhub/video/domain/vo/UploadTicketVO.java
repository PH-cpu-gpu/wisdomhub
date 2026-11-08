package com.wisdomhub.video.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadTicketVO {

    private Long assetId;

    private String objectKey;

    private String uploadUrl;

    private String method;

    /** 直传时必须原样携带的请求头 */
    private Map<String, String> headers;

    /** UTC */
    private LocalDateTime expiresAt;
}
