package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.ColumnCreateDTO;
import com.wisdomhub.content.domain.vo.ArticleListItemVO;
import com.wisdomhub.content.domain.vo.ColumnVO;
import com.wisdomhub.content.service.ColumnService;
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

@Tag(name = "专栏controller")
@Validated
@RestController
@RequestMapping("/api/content/column")
@RequiredArgsConstructor
public class ColumnController {

    private final ColumnService columnService;

    @Operation(summary = "创建专栏")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ColumnCreateDTO columnCreateDTO) {
        return R.success(columnService.createColumn(columnCreateDTO));
    }

    @Operation(summary = "我的专栏")
    @GetMapping("/mine")
    public R<PageResponse<ColumnVO>> mine(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(columnService.listMyColumns(page, pageSize));
    }

    @Operation(summary = "专栏详情")
    @GetMapping("/{columnId}")
    public R<ColumnVO> detail(@PathVariable Long columnId) {
        return R.success(columnService.getColumnDetail(columnId));
    }

    @Operation(summary = "专栏内文章分页")
    @GetMapping("/{columnId}/article")
    public R<PageResponse<ArticleListItemVO>> articles(@PathVariable Long columnId,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(columnService.listColumnArticles(columnId, page, pageSize));
    }

    @Operation(summary = "归入文章")
    @PutMapping("/{columnId}/article/{articleId}")
    public R<Integer> addArticle(@PathVariable Long columnId, @PathVariable Long articleId) {
        return R.success(columnService.addArticle(columnId, articleId));
    }

    @Operation(summary = "移出文章")
    @DeleteMapping("/{columnId}/article/{articleId}")
    public R<Integer> removeArticle(@PathVariable Long columnId, @PathVariable Long articleId) {
        return R.success(columnService.removeArticle(columnId, articleId));
    }
}
