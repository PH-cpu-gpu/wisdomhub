package com.wisdomhub.common.feign.client;

import com.wisdomhub.common.feign.dto.ContentTagDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** content-service 标签字典客户端。标签治理仍在 content，调用方只读。 */
@FeignClient(name = "content-service", contextId = "contentTag", path = "/internal/content")
public interface ContentTagFeign {

    @GetMapping("/tag/all")
    List<ContentTagDTO> listAllTags();
}
