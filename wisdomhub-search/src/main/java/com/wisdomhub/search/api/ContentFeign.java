package com.wisdomhub.search.api;

import com.wisdomhub.search.api.dto.ArticleInternalDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * content-service 的内部读接口。common-feign 的 RunwrapDecoder 会把 R<T> 拆掉直接返回 T，
 * 下游 code != 200 时抛 BusinessException —— 消费者靠它区分 404（文章没了）与 5xx（该重试）。
 */
@FeignClient(name = "content-service", contextId = "contentArticle", path = "/internal/content")
public interface ContentFeign {

    @GetMapping("/article/{articleId}")
    ArticleInternalDTO getArticle(@PathVariable("articleId") Long articleId);
}
