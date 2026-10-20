package com.wisdomhub.knowledge.api;

import com.wisdomhub.knowledge.api.dto.ArticleBriefDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** content-service 的内部文章摘要接口，给图谱可视化的文章节点补标题 */
@FeignClient(name = "content-service", contextId = "articleBrief", path = "/internal/content")
public interface ArticleBriefFeign {

    /** 批量取文章摘要；返回顺序不保证与入参一致，调用方要自己按 id 重排 */
    @GetMapping("/brief")
    List<ArticleBriefDTO> listBrief(@RequestParam("articleIds") List<Long> articleIds);

    /** 批量判活：只返回存在且未删除的文章 ID，供图谱对账 */
    @GetMapping("/article/exists")
    List<Long> existingArticleIds(@RequestParam("articleIds") List<Long> articleIds);
}
