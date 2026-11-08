package com.wisdomhub.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wisdomhub.video.playback")
public class PlaybackProperties {

    /** 播放分片/封面签名有效期（秒） */
    private int urlTtlSeconds = 3600;

    /** 预留：CDN 域名 */
    private String cdnDomain;

    /** 预留：CDN 鉴权 key */
    private String cdnAuthKey;
}
