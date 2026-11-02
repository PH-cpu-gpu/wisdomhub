package com.wisdomhub.knowledge.api;

import com.wisdomhub.knowledge.api.dto.TagInternalDTO;
import com.wisdomhub.knowledge.api.dto.TagRelationInternalDTO;
import com.wisdomhub.knowledge.api.dto.TagResolveDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** content-service 的内部标签读接口，供图谱种子导入、对账与实体名解析 */
@FeignClient(name = "content-service", contextId = "contentTag", path = "/internal/content")
public interface ContentTagFeign {

    @GetMapping("/tag/all")
    List<TagInternalDTO> listAllTags();

    @GetMapping("/tag-relation/all")
    List<TagRelationInternalDTO> listAllRelations();

    @PostMapping("/tag/resolve")
    List<TagResolveDTO> resolveTags(@RequestBody List<String> names);
}
