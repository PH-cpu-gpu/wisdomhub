package com.wisdomhub.user.api;

import com.wisdomhub.user.api.dto.ArticleBriefDTO;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "content-service", contextId = "article", path = "/internal/content")
public interface ArticleFeign {

    @GetMapping("/count/{userId}")
    Long countArticle(@NotNull @PathVariable Long userId);

    /** 批量取文章摘要；返回顺序不保证与入参一致，调用方要自己按 id 重排 */
    @GetMapping("/brief")
    List<ArticleBriefDTO> listBrief(@RequestParam("articleIds") List<Long> articleIds);
}
