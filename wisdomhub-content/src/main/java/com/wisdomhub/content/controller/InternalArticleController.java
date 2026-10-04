package com.wisdomhub.content.controller;


import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.vo.ArticleBriefVO;
import com.wisdomhub.content.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "服务间文章controller")
@Validated
@RestController
@RequestMapping("/internal/content")
@RequiredArgsConstructor
public class InternalArticleController {

    private final ArticleService articleService;

    @Operation(summary = "根据用户ID查询文章数量")
    @GetMapping("/count/{userId}")
    public R<Long> countArticle(@NotNull @PathVariable Long userId) {
        return R.success(articleService.countArticleByUserId(userId));
    }

    @Operation(summary = "按ID批量查询文章摘要")
    @GetMapping("/brief")
    public R<List<ArticleBriefVO>> listBrief(@RequestParam("articleIds") List<Long> articleIds) {
        return R.success(articleService.listBrief(articleIds));
    }
}
