package com.wisdomhub.search.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.search.domain.vo.ArticleSearchVO;
import com.wisdomhub.search.domain.vo.QuestionSearchVO;
import com.wisdomhub.search.domain.vo.TagSearchVO;
import com.wisdomhub.search.domain.vo.UserSearchVO;
import com.wisdomhub.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "搜索controller")
@Validated
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "文章搜索：关键词高亮 + 标签/时间过滤 + 相关性与热度排序")
    @GetMapping("/article")
    public R<PageResponse<ArticleSearchVO>> articles(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.success(searchService.searchArticles(keyword, page, pageSize, sort, tagId, startTime, endTime));
    }

    @Operation(summary = "问题搜索：关键词高亮 + 时间过滤 + 相关性/最新/回答数排序")
    @GetMapping("/question")
    public R<PageResponse<QuestionSearchVO>> questions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.success(searchService.searchQuestions(keyword, page, pageSize, sort, startTime, endTime));
    }

    @Operation(summary = "用户搜索：用户名与简介")
    @GetMapping("/user")
    public R<PageResponse<UserSearchVO>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String sort) {
        return R.success(searchService.searchUsers(keyword, page, pageSize, sort));
    }

    @Operation(summary = "标签搜索：标签名与简介，可按展示分组过滤")
    @GetMapping("/tag")
    public R<PageResponse<TagSearchVO>> tags(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String category) {
        return R.success(searchService.searchTags(keyword, page, pageSize, sort, category));
    }
}
