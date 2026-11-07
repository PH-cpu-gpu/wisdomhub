package com.wisdomhub.common.feign.client;

import org.springframework.cloud.openfeign.FeignClient;

/** content-service 的目标校验客户端。 */
@FeignClient(name = "content-service", contextId = "contentTargetProvider")
public interface ContentTargetProviderFeign extends TargetProviderFeign {
}
