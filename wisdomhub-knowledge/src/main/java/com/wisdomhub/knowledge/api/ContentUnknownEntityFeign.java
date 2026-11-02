package com.wisdomhub.knowledge.api;

import com.wisdomhub.knowledge.api.dto.UnknownEntityHitDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** content-service 的未收录实体采集入口；只写证据，升级成提案在 content 侧完成 */
@FeignClient(name = "content-service", contextId = "contentUnknownEntity", path = "/internal/content")
public interface ContentUnknownEntityFeign {

    /** 返回本次真正新增的证据条数 */
    @PostMapping("/unknown-entity/hits")
    Integer recordHits(@RequestBody UnknownEntityHitDTO dto);
}
