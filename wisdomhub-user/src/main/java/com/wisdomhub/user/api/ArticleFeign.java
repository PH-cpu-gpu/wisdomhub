package com.wisdomhub.user.api;

import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "content-service", contextId = "article", path = "/internal/content")
public interface ArticleFeign {

    @GetMapping("/count/{userId}")
    Long countArticle(@NotNull @PathVariable Long userId);
}
