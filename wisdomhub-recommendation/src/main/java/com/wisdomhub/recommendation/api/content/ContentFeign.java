package com.wisdomhub.recommendation.api.content;

import com.wisdomhub.recommendation.api.content.dto.ArticleBriefDTO;
import com.wisdomhub.recommendation.api.content.dto.TagDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** content-service 的文章摘要与标签字典；推荐侧不直连 MySQL */
@FeignClient(name = "content-service", contextId = "contentArticle", path = "/internal/content")
public interface ContentFeign {

    @GetMapping("/brief")
    List<ArticleBriefDTO> listBrief(@RequestParam("articleIds") List<Long> articleIds);

    @GetMapping("/tag/all")
    List<TagDTO> listAllTags();
}
