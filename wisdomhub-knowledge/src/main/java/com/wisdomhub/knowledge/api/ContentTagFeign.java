package com.wisdomhub.knowledge.api;

import com.wisdomhub.knowledge.api.dto.TagInternalDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** content-service 的内部标签读接口，供图谱种子导入 */
@FeignClient(name = "content-service", contextId = "contentTag", path = "/internal/content")
public interface ContentTagFeign {

    @GetMapping("/tag/all")
    List<TagInternalDTO> listAllTags();
}
