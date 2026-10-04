package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.ArticlePublishDTO;
import com.wisdomhub.content.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
