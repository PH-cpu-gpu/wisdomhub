package com.wisdomhub.user.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.user.domain.vo.BrowseHistoryVO;
import com.wisdomhub.user.service.BrowseHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "浏览历史controller")
@Validated
@RestController
@RequestMapping("/api/user/history")
@RequiredArgsConstructor
public class BrowseHistoryController {

    private final BrowseHistoryService browseHistoryService;

    @Operation(summary = "记录一次浏览")
    @PutMapping("/{articleId}")
    public R<Void> record(@PathVariable Long articleId) {
        browseHistoryService.record(articleId);
        return R.success();
    }

    @Operation(summary = "浏览历史列表")
    @GetMapping
    public R<PageResponse<BrowseHistoryVO>> list(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(browseHistoryService.list(page, pageSize));
    }

    @Operation(summary = "删除一条浏览历史")
    @DeleteMapping("/{articleId}")
    public R<Void> delete(@PathVariable Long articleId) {
        browseHistoryService.delete(articleId);
        return R.success();
    }

    @Operation(summary = "清空浏览历史")
    @DeleteMapping
    public R<Void> clear() {
        browseHistoryService.clear();
        return R.success();
    }
}
