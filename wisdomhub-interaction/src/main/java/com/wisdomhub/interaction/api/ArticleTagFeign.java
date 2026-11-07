package com.wisdomhub.interaction.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/** content-service 的文章标签 ID 接口，行为事件需要 tagIds。 */
@FeignClient(name = "content-service", contextId = "interactionArticleTag", path = "/internal/content")
public interface ArticleTagFeign {

    @GetMapping("/article/{articleId}/tag/ids")
    List<Long> listTagIds(@PathVariable("articleId") Long articleId);
}
