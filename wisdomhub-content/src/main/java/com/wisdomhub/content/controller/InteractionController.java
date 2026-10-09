package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.service.InteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "点赞收藏controller")
@Validated
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    @Operation(summary = "点赞")
    @PutMapping("/article/{articleId}/like")
    public R<Integer> like(@PathVariable Long articleId) {
        return R.success(interactionService.like(articleId));
    }

    @Operation(summary = "取消点赞")
    @DeleteMapping("/article/{articleId}/like")
    public R<Integer> unlike(@PathVariable Long articleId) {
        return R.success(interactionService.unlike(articleId));
    }

    @Operation(summary = "收藏")
    @PutMapping("/article/{articleId}/collect")
    public R<Integer> collect(@PathVariable Long articleId) {
        return R.success(interactionService.collect(articleId));
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/article/{articleId}/collect")
    public R<Integer> uncollect(@PathVariable Long articleId) {
        return R.success(interactionService.uncollect(articleId));
    }
}
