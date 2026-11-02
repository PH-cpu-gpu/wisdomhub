package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.common.web.annotation.RequireAdmin;
import com.wisdomhub.content.domain.vo.TagProposalVO;
import com.wisdomhub.content.service.TagProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "标签提案controller")
@Validated
@RestController
@RequestMapping("/api/content/tag-proposal")
@RequiredArgsConstructor
@RequireAdmin
public class TagProposalController {

    private final TagProposalService tagProposalService;

    @Operation(summary = "提案分页")
    @GetMapping
    public R<PageResponse<TagProposalVO>> page(@RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) Integer proposalType,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(tagProposalService.page(status, proposalType, page, pageSize));
    }

    @Operation(summary = "提案详情")
    @GetMapping("/{id}")
    public R<TagProposalVO> detail(@PathVariable Long id) {
        return R.success(tagProposalService.detail(id));
    }

    @Operation(summary = "通过提案")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        tagProposalService.approve(id);
        return R.success();
    }

    @Operation(summary = "拒绝提案")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id) {
        tagProposalService.reject(id);
        return R.success();
    }
}
