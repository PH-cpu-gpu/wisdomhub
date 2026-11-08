package com.wisdomhub.common.feign.client;

import org.springframework.cloud.openfeign.FeignClient;

/** video-service 的目标校验客户端。 */
@FeignClient(name = "video-service", contextId = "videoTargetProvider")
public interface VideoTargetProviderFeign extends TargetProviderFeign {
}
