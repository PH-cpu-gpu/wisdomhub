package com.wisdomhub.common.feign.client;

import com.wisdomhub.common.feign.dto.TargetInfoDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 内容类服务统一的目标校验契约。
 * type 取 {@code ContentTargetTypeConstants} 的 *_NAME，例如 ARTICLE。
 */
public interface TargetProviderFeign {

    @GetMapping("/internal/targets/{type}/{id}")
    TargetInfoDTO getTarget(@PathVariable("type") String type, @PathVariable("id") Long id);
}
