package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.ArticleDraftDTO;
import com.wisdomhub.content.domain.vo.DraftBriefVO;
import com.wisdomhub.content.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "草稿controller")
@Validated
@RestController
@RequestMapping("/api/content/draft")
@RequiredArgsConstructor
public class DraftController {

    private final ArticleService articleService;

    @Operation(summary = "新建草稿（首次自动保存）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ArticleDraftDTO articleDraftDTO) {
        return R.success(articleService.createDraft(articleDraftDTO));
    }

    @Operation(summary = "更新草稿（后续自动保存）")
    @PutMapping("/{articleId}")
    public R<Long> update(@PathVariable Long articleId, @Valid @RequestBody ArticleDraftDTO articleDraftDTO) {
        return R.success(articleService.updateDraft(articleId, articleDraftDTO));
    }

    @Operation(summary = "草稿箱分页列表")
    @GetMapping
    public R<PageResponse<DraftBriefVO>> list(@RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(articleService.listDrafts(page, pageSize));
    }

    @Operation(summary = "删除草稿")
    @DeleteMapping("/{articleId}")
    public R<Void> delete(@PathVariable Long articleId) {
        articleService.deleteDraft(articleId);
        return R.success();
    }
}
