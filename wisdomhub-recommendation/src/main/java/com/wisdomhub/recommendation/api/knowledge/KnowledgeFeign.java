package com.wisdomhub.recommendation.api.knowledge;

import com.wisdomhub.recommendation.api.knowledge.dto.TagRelationsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** knowledge-service 的技术关系；推荐侧不直连 Neo4j */
@FeignClient(name = "knowledge-service", contextId = "knowledgeGraph", path = "/internal/knowledge")
public interface KnowledgeFeign {

    @GetMapping("/related")
    TagRelationsDTO relatedByTech(@RequestParam("tech") String tech);

    @GetMapping("/tag/{tagId}/related")
    TagRelationsDTO relatedByTagId(@PathVariable("tagId") String tagId);
}
