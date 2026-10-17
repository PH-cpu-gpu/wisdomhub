package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.ArticlePublishDTO;
import com.wisdomhub.content.domain.vo.ArticleBriefVO;
import com.wisdomhub.content.domain.vo.ArticleDetailVO;
import com.wisdomhub.content.domain.vo.ArticleListItemVO;
import com.wisdomhub.content.domain.vo.ArticleSourceVO;
import com.wisdomhub.content.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "删除文章")
    @DeleteMapping("/article/{articleId}")
    public R<Void> delete(@PathVariable Long articleId) {
        articleService.deleteArticle(articleId);
        return R.success();
    }

    @Operation(summary = "文章列表")
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

    @Operation(summary = "我的收藏")
    @GetMapping("/article/collect")
    public R<PageResponse<ArticleListItemVO>> myFavorites(@RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(articleService.listMyFavorites(page, pageSize));
    }

    @Operation(summary = "按 ID 批量取文章摘要")
    @GetMapping("/article/brief")
    public R<List<ArticleBriefVO>> listBrief(@RequestParam("articleIds") List<Long> articleIds) {
        return R.success(articleService.listBrief(articleIds));
    }

    @Operation(summary = "读取编辑器源数据（草稿恢复 / 编辑已发布）")
    @GetMapping("/article/{articleId}/source")
    public R<ArticleSourceVO> source(@PathVariable Long articleId) {
        return R.success(articleService.getSource(articleId));
    }
}
