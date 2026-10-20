package com.wisdomhub.search.api;

import com.wisdomhub.search.api.dto.ArticleInternalDTO;
import com.wisdomhub.search.api.dto.QuestionSyncDTO;
import com.wisdomhub.search.api.dto.TagSyncDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * content-service 的内部读接口。common-feign 的 RunwrapDecoder 会把 R<T> 拆掉直接返回 T，
 * 下游 code != 200 时抛 BusinessException —— 消费者靠它区分 404（文章没了）与 5xx（该重试）。
 */
@FeignClient(name = "content-service", contextId = "contentArticle", path = "/internal/content")
public interface ContentFeign {

    @GetMapping("/article/{articleId}")
    ArticleInternalDTO getArticle(@PathVariable("articleId") Long articleId);

    /** since 用 ISO 字符串传，避免 Feign 对 LocalDateTime 的编码差异；全量扫传 1970-01-01T00:00:00 */
    @GetMapping("/article/sync")
    List<ArticleInternalDTO> syncArticles(@RequestParam("since") String since,
                                          @RequestParam("lastId") Long lastId,
                                          @RequestParam("size") Integer size);

    @GetMapping("/tag/sync")
    List<TagSyncDTO> syncTags(@RequestParam("since") String since,
                              @RequestParam("lastId") Long lastId,
                              @RequestParam("size") Integer size);

    @GetMapping("/question/sync")
    List<QuestionSyncDTO> syncQuestions(@RequestParam("since") String since,
                                        @RequestParam("lastId") Long lastId,
                                        @RequestParam("size") Integer size);
}
