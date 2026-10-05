package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.ArticlePublishDTO;
import com.wisdomhub.content.domain.vo.ArticleDetailVO;
import com.wisdomhub.content.domain.vo.ArticleListItemVO;
import com.wisdomhub.content.domain.vo.ArticleSourceVO;
import com.wisdomhub.content.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "文章controller")
@Validated
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @Operation(summary = "发布文章")
    @PostMapping("/article")
    public R<Long> publish(@Valid @RequestBody ArticlePublishDTO articlePublishDTO) {
        return R.success(articleService.publish(articlePublishDTO));
    }

    @Operation(summary = "文章列表（分页 / 标签过滤 / 排序）")
    @GetMapping("/article")
    public R<PageResponse<ArticleListItemVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(defaultValue = "new") String sort,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long authorId) {
        return R.success(articleService.listArticles(page, pageSize, sort, tagId, authorId));
    }

    @Operation(summary = "文章详情")
    @GetMapping("/article/{articleId}")
    public R<ArticleDetailVO> detail(@PathVariable Long articleId) {
        return R.success(articleService.getDetail(articleId));
    }

    @Operation(summary = "读取编辑器源数据（草稿恢复 / 编辑已发布）")
    @GetMapping("/article/{articleId}/source")
    public R<ArticleSourceVO> source(@PathVariable Long articleId) {
        return R.success(articleService.getSource(articleId));
    }
}
